#!/usr/bin/env python3
"""
围棋死活题19路到13路转换脚本 v4
核心改动：包含所有题库，不再过滤任何书籍
目标：将围棋大全的全部题库整合进APK

v3.1 → v4:
- 移除 BOOKS_TO_DELETE（不再排除任何题库）
- MIN_BOOK_SIZE 降为 1（保留所有题目）
- 其余逻辑与v3.1一致
"""

import json, zlib, copy
from collections import Counter

BOARD_19 = 19
BOARD_13 = 13
MAX_19 = 18
MAX_13 = 12


def calc_offset(min_c, max_c, board_from, board_to):
    """计算平移偏移量，按比例保留间距"""
    width = max_c - min_c + 1
    gap_start = min_c
    gap_end = board_from - 1 - max_c
    
    if width > board_to:
        return None
    
    extra = board_to - width
    total_gap = gap_start + gap_end
    
    if total_gap == 0:
        new_gap_start = extra // 2
    else:
        new_gap_start = round(extra * gap_start / total_gap)
    
    return new_gap_start - min_c


def convert_problem(problem):
    # 统一到top-down坐标系
    stones_td = [[s[0], MAX_19 - s[1], s[2]] for s in problem['stones']]
    answer_td = [problem['answer'][0], problem['answer'][1]]
    moves_td = [[m[0], m[1], m[2]] for m in problem.get('solutionMoves', [])]
    
    all_points = [(s[0], s[1]) for s in stones_td] + [tuple(answer_td)]
    for m in moves_td:
        all_points.append((m[0], m[1]))
    
    min_x = min(p[0] for p in all_points)
    max_x = max(p[0] for p in all_points)
    min_y = min(p[1] for p in all_points)
    max_y = max(p[1] for p in all_points)
    
    offset_x = calc_offset(min_x, max_x, BOARD_19, BOARD_13)
    offset_y = calc_offset(min_y, max_y, BOARD_19, BOARD_13)
    
    if offset_x is None or offset_y is None:
        return None, "too_large"
    
    new_stones = [[s[0]+offset_x, s[1]+offset_y, s[2]] for s in stones_td]
    new_answer = [answer_td[0]+offset_x, answer_td[1]+offset_y]
    new_moves = [[m[0]+offset_x, m[1]+offset_y, m[2]] for m in moves_td]
    
    # 验证范围
    for s in new_stones:
        if not (0 <= s[0] <= MAX_13 and 0 <= s[1] <= MAX_13):
            return None, "stone_out_of_range"
    if not (0 <= new_answer[0] <= MAX_13 and 0 <= new_answer[1] <= MAX_13):
        return None, "answer_out_of_range"
    for m in new_moves:
        if not (0 <= m[0] <= MAX_13 and 0 <= m[1] <= MAX_13):
            return None, "move_out_of_range"
    
    new_problem = copy.deepcopy(problem)
    new_problem['boardSize'] = BOARD_13
    new_problem['stones'] = new_stones
    new_problem['answer'] = new_answer
    new_problem['solutionMoves'] = new_moves
    return new_problem, "ok"


def get_group(board, x, y, visited=None):
    if visited is None: visited = set()
    if (x,y) in visited: return visited
    if x<0 or x>=BOARD_13 or y<0 or y>=BOARD_13: return visited
    c = board[y][x]
    if c==0: return visited
    visited.add((x,y))
    for dx,dy in [(-1,0),(1,0),(0,-1),(0,1)]:
        nx,ny=x+dx,y+dy
        if (nx,ny) not in visited and 0<=nx<BOARD_13 and 0<=ny<BOARD_13 and board[ny][nx]==c:
            get_group(board, nx, ny, visited)
    return visited

def count_lib(board, group):
    libs = set()
    for x,y in group:
        for dx,dy in [(-1,0),(1,0),(0,-1),(0,1)]:
            nx,ny=x+dx,y+dy
            if 0<=nx<BOARD_13 and 0<=ny<BOARD_13 and board[ny][nx]==0: libs.add((nx,ny))
    return len(libs)

def place_stone_on_board(board, x, y, color):
    if board[y][x] != 0: return None
    new_board = [row[:] for row in board]
    new_board[y][x] = color
    opp = 3 - color
    for dx, dy in [(-1,0),(1,0),(0,-1),(0,1)]:
        nx, ny = x+dx, y+dy
        if 0<=nx<BOARD_13 and 0<=ny<BOARD_13 and new_board[ny][nx]==opp:
            g = get_group(new_board, nx, ny)
            if count_lib(new_board, g)==0:
                for gx,gy in g: new_board[gy][gx]=0
    own = get_group(new_board, x, y)
    if count_lib(new_board, own)==0: return None
    return new_board

def simulate_and_verify(problem):
    board = [[0]*BOARD_13 for _ in range(BOARD_13)]
    for s in problem['stones']:
        if board[s[1]][s[0]] != 0: return False, "overlap"
        board[s[1]][s[0]] = s[2]
    ax, ay = problem['answer']
    if board[ay][ax] != 0: return False, "answer_blocked"
    
    playable_count = 0
    for m in problem['solutionMoves']:
        mx, my, mc = m[0], m[1], m[2]
        new_board = place_stone_on_board(board, mx, my, mc)
        if new_board is not None:
            board = new_board
            playable_count += 1
    
    if playable_count == 0 and len(problem['solutionMoves']) > 0:
        return False, "no_playable_moves"
    return True, "ok"


def main():
    with open('original_19x19.json', 'r') as f:
        problems = json.load(f)
    print(f"原始: {len(problems)} 题")
    
    # v4: 不过滤任何题库，全部保留
    book_counts_before = Counter(p.get('book','') for p in problems)
    print(f"题库数: {len(book_counts_before)} 个")
    print("\n原始题库分布:")
    for book, count in book_counts_before.most_common():
        print(f"  {book}: {count}")
    
    # 转换
    converted = []
    stats = Counter()
    for p in problems:
        new_p, status = convert_problem(p)
        stats[status] += 1
        if new_p: converted.append(new_p)
    
    print(f"\n转换结果: 成功 {len(converted)}, 失败统计: {dict(stats)}")
    
    # 验证
    valid = []
    invalid = Counter()
    for p in converted:
        ok, reason = simulate_and_verify(p)
        if ok: valid.append(p)
        else: invalid[reason] += 1
    
    print(f"验证结果: 通过 {len(valid)}, 失败: {dict(invalid)}")
    
    # 题库分布
    books = Counter(p.get('book','') for p in valid)
    print(f"\n最终题库({len(books)}个):")
    for book, count in books.most_common():
        print(f"  {book}: {count}")
    
    # 保存
    json_str = json.dumps(valid, ensure_ascii=False, separators=(',', ':'))
    with open('app/src/main/assets/problems_full.json', 'w', encoding='utf-8') as f:
        f.write(json_str)
    compressed = zlib.compress(json_str.encode('utf-8'))
    with open('app/src/main/assets/problems_compressed.bin', 'wb') as f:
        f.write(compressed)
    
    import os
    print(f"\n输出文件:")
    print(f"  JSON: {os.path.getsize('app/src/main/assets/problems_full.json')//1024}KB")
    print(f"  压缩: {len(compressed)//1024}KB")
    print(f"  总题数: {len(valid)}")
    print(f"  题库数: {len(books)}")

if __name__ == '__main__':
    main()
