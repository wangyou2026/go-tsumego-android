#!/usr/bin/env python3
"""
围棋死活题19路到13路转换脚本 v3
核心改动：按比例保留间距，而不是硬对齐到边

原则：
- 贴边的保持贴边（gap=0 → gap=0）
- 不贴边的保留间距（按比例缩放gap）
- 这样"差一格到底边"的题目转换后也是"差一格到底边"

v3.1: 移除 process_solution_moves 截断逻辑，直接使用原始 solutionMoves
      原始数据中 solutionMoves 包含多个分支（正解+变化），播放时跳过无法落子的步骤
      验证逻辑也相应改为跳过 blocked/self-capture 的步骤
"""

import json, zlib, copy
from collections import Counter

BOOKS_TO_DELETE = ['其他', '官子谱', '玄玄棋经', '仙机武库', '郑']
MIN_BOOK_SIZE = 50
BOARD_19 = 19
BOARD_13 = 13
MAX_19 = 18
MAX_13 = 12


def calc_offset(min_c, max_c, board_from, board_to):
    """计算平移偏移量，按比例保留间距"""
    width = max_c - min_c + 1
    gap_start = min_c           # 距起始边的间距
    gap_end = board_from - 1 - max_c  # 距结束边的间距
    
    if width > board_to:
        return None  # 放不下
    
    extra = board_to - width
    total_gap = gap_start + gap_end
    
    if total_gap == 0:
        new_gap_start = extra // 2  # 居中
    else:
        new_gap_start = round(extra * gap_start / total_gap)
    
    return new_gap_start - min_c


def convert_problem(problem):
    # 统一到top-down坐标系
    stones_td = [[s[0], MAX_19 - s[1], s[2]] for s in problem['stones']]
    answer_td = [problem['answer'][0], problem['answer'][1]]
    # 直接使用原始 solutionMoves，不做任何截断
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
    """Place a stone and handle captures, return new board or None if blocked/self-capture"""
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
    """验证问题 - 模拟所有解题步骤，跳过无法落子的步骤（与App行为一致）"""
    board = [[0]*BOARD_13 for _ in range(BOARD_13)]
    for s in problem['stones']:
        if board[s[1]][s[0]] != 0: return False, "overlap"
        board[s[1]][s[0]] = s[2]
    ax, ay = problem['answer']
    if board[ay][ax] != 0: return False, "answer_blocked"
    
    # 模拟解题步骤，跳过无法落子的步骤（与App行为一致）
    playable_count = 0
    for m in problem['solutionMoves']:
        mx, my, mc = m[0], m[1], m[2]
        new_board = place_stone_on_board(board, mx, my, mc)
        if new_board is not None:
            board = new_board
            playable_count += 1
    
    # 至少要有可播放的步骤才算有效
    if playable_count == 0 and len(problem['solutionMoves']) > 0:
        return False, "no_playable_moves"
    return True, "ok"


def main():
    with open('original_19x19.json', 'r') as f:
        problems = json.load(f)
    print(f"原始: {len(problems)}")
    
    # 过滤题库
    filtered = [p for p in problems if p.get('book','') not in BOOKS_TO_DELETE]
    book_counts = Counter(p.get('book','') for p in filtered)
    valid_books = {b for b, c in book_counts.items() if c >= MIN_BOOK_SIZE}
    final = [p for p in filtered if p.get('book','') in valid_books]
    print(f"过滤后: {len(final)} (删除题库: {[b for b,c in book_counts.items() if c < MIN_BOOK_SIZE]})")
    
    # 转换
    converted = []
    stats = Counter()
    for p in final:
        new_p, status = convert_problem(p)
        stats[status] += 1
        if new_p: converted.append(new_p)
    
    print(f"转换: {len(converted)}, 失败: {dict(stats)}")
    
    # 验证
    valid = []
    invalid = Counter()
    for p in converted:
        ok, reason = simulate_and_verify(p)
        if ok: valid.append(p)
        else: invalid[reason] += 1
    
    print(f"验证通过: {len(valid)}, 失败: {dict(invalid)}")
    
    # 题库分布
    books = Counter(p.get('book','') for p in valid)
    print(f"\n题库({len(books)}个):")
    for book, count in books.most_common():
        print(f"  {book}: {count}")
    
    # 保存
    with open('app/src/main/assets/problems_full.json', 'w', encoding='utf-8') as f:
        json.dump(valid, f, ensure_ascii=False, separators=(',', ':'))
    json_str = json.dumps(valid, ensure_ascii=False, separators=(',', ':'))
    compressed = zlib.compress(json_str.encode('utf-8'))
    with open('app/src/main/assets/problems_compressed.bin', 'wb') as f:
        f.write(compressed)
    
    import os
    print(f"\nJSON: {os.path.getsize('app/src/main/assets/problems_full.json')//1024}KB, 压缩: {len(compressed)//1024}KB")
    
    # 抽样验证前田陈尔#3
    maeda = [p for p in valid if p.get('book')=='前田陈尔']
    if len(maeda) >= 3:
        p = maeda[2]
        board = [['.' for _ in range(13)] for _ in range(13)]
        for s in p['stones']: board[s[1]][s[0]] = 'B' if s[2]==1 else 'W'
        ax, ay = p['answer']; board[ay][ax] = 'A'
        print(f"\n前田陈尔#3 验证 ({p['title']}):")
        for y in range(13):
            row = ''.join(board[y])
            if row.strip('.'): print(f"  {y:2d}: {row}")
        all_pts = [(s[0],s[1]) for s in p['stones']]+[(ax,ay)]
        min_y = min(pt[1] for pt in all_pts)
        max_y = max(pt[1] for pt in all_pts)
        print(f"  y range: {min_y}-{max_y}, gap_to_bottom={12-max_y}")

if __name__ == '__main__':
    main()
