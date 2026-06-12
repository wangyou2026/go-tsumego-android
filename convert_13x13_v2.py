#!/usr/bin/env python3
"""
围棋死活题19路到13路转换脚本 v2
核心思路：将题目整体平移到13路棋盘合适位置，不做任何翻转

步骤：
1. 将stones从bottom-up转为top-down（y → 18-y）
2. answer/solutionMoves已经是top-down，直接用
3. 统一在top-down坐标系下计算包围框
4. 检查在19路棋盘上接触哪些边
5. 计算平移量，将题目移动到13路棋盘的对应位置
6. 验证并输出
"""

import json
import zlib
import copy

BOOKS_TO_DELETE = ['其他', '官子谱', '玄玄棋经', '仙机武库', '郑']
BOARD_19 = 19
BOARD_13 = 13
MAX_COORD_19 = 18
MAX_COORD_13 = 12


def process_solution_moves(solution_moves):
    """处理solutionMoves：只取第一个变化图"""
    if not solution_moves:
        return solution_moves
    
    processed = []
    for i, move in enumerate(solution_moves):
        if i == 0:
            processed.append(move)
        else:
            # 连续同色步 = 变化图分割点
            if solution_moves[i-1][2] == move[2]:
                break
            processed.append(move)
    
    # 去重
    seen = set()
    result = []
    for move in processed:
        key = (move[0], move[1], move[2])
        if key in seen:
            break
        seen.add(key)
        result.append(move)
    
    return result


def convert_problem(problem):
    """转换单个题目：纯平移，不翻转"""
    # Step 1: 统一到top-down坐标系
    # stones的y是bottom-up，转为top-down
    stones_td = [[s[0], MAX_COORD_19 - s[1], s[2]] for s in problem['stones']]
    
    # answer和solutionMoves已经是top-down，直接用
    answer_td = [problem['answer'][0], problem['answer'][1]]
    moves_raw = problem.get('solutionMoves', [])
    moves_processed = process_solution_moves(moves_raw)
    moves_td = [[m[0], m[1], m[2]] for m in moves_processed]
    
    # Step 2: 收集所有点
    all_points = [(s[0], s[1]) for s in stones_td]
    all_points.append(tuple(answer_td))
    for m in moves_td:
        all_points.append((m[0], m[1]))
    
    # Step 3: 包围框
    min_x = min(p[0] for p in all_points)
    max_x = max(p[0] for p in all_points)
    min_y = min(p[1] for p in all_points)
    max_y = max(p[1] for p in all_points)
    
    width = max_x - min_x + 1
    height = max_y - min_y + 1
    
    # Step 4: 检查是否超过13路
    if width > BOARD_13 or height > BOARD_13:
        return None, "too_large"
    
    # Step 5: 检查在19路棋盘上接触的边
    touches_left = (min_x == 0)
    touches_right = (max_x == MAX_COORD_19)
    touches_top = (min_y == 0)
    touches_bottom = (max_y == MAX_COORD_19)
    
    # Step 6: 计算平移量
    # X轴
    if touches_left and touches_right:
        # 同时接触左右边，只有width=19才行，但已经>13了，不会到这
        offset_x = 0
    elif touches_left:
        # 接触左边，保持贴左
        offset_x = -min_x  # = 0
    elif touches_right:
        # 接触右边，保持贴右
        offset_x = MAX_COORD_13 - max_x
    else:
        # 不接触任何边，选择靠得近的一边对齐
        dist_to_left = min_x
        dist_to_right = MAX_COORD_19 - max_x
        if dist_to_left <= dist_to_right:
            offset_x = -min_x  # 左对齐
        else:
            offset_x = MAX_COORD_13 - max_x  # 右对齐
    
    # Y轴
    if touches_top and touches_bottom:
        offset_y = 0
    elif touches_top:
        offset_y = -min_y  # = 0
    elif touches_bottom:
        offset_y = MAX_COORD_13 - max_y
    else:
        dist_to_top = min_y
        dist_to_bottom = MAX_COORD_19 - max_y
        if dist_to_top <= dist_to_bottom:
            offset_y = -min_y
        else:
            offset_y = MAX_COORD_13 - max_y
    
    # Step 7: 平移所有坐标
    new_stones = [[s[0] + offset_x, s[1] + offset_y, s[2]] for s in stones_td]
    new_answer = [answer_td[0] + offset_x, answer_td[1] + offset_y]
    new_moves = [[m[0] + offset_x, m[1] + offset_y, m[2]] for m in moves_td]
    
    # Step 8: 验证范围
    for s in new_stones:
        if not (0 <= s[0] <= MAX_COORD_13 and 0 <= s[1] <= MAX_COORD_13):
            return None, "stone_out_of_range"
    if not (0 <= new_answer[0] <= MAX_COORD_13 and 0 <= new_answer[1] <= MAX_COORD_13):
        return None, "answer_out_of_range"
    for m in new_moves:
        if not (0 <= m[0] <= MAX_COORD_13 and 0 <= m[1] <= MAX_COORD_13):
            return None, "move_out_of_range"
    
    # Step 9: 构建新题目
    new_problem = copy.deepcopy(problem)
    new_problem['boardSize'] = BOARD_13
    new_problem['stones'] = new_stones
    new_problem['answer'] = new_answer
    new_problem['solutionMoves'] = new_moves
    
    return new_problem, "ok"


def simulate_and_verify(problem):
    """完整模拟验证"""
    board = [['.' for _ in range(BOARD_13)] for _ in range(BOARD_13)]
    
    # 放置初始棋子
    for stone in problem['stones']:
        x, y, color = stone
        if board[y][x] != '.':
            return False, f"stones_overlap_at_{x}_{y}"
        board[y][x] = 'B' if color == 1 else 'W'
    
    # 检查answer在空位
    ax, ay = problem['answer']
    if board[ay][ax] != '.':
        return False, f"answer_blocked_at_{ax}_{ay}"
    
    # 模拟solutionMoves
    for move in problem['solutionMoves']:
        x, y, color = move
        if board[y][x] != '.':
            return False, f"move_blocked_at_{x}_{y}"
        
        board[y][x] = 'B' if color == 1 else 'W'
        opponent = 3 - color
        opp_char = 'W' if opponent == 1 else 'B'
        
        for dx, dy in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
            nx, ny = x + dx, y + dy
            if 0 <= nx < BOARD_13 and 0 <= ny < BOARD_13:
                if board[ny][nx] == opp_char:
                    if not has_liberty(board, nx, ny, opp_char):
                        remove_group(board, nx, ny, opp_char)
        
        # 检查自杀
        own_char = 'B' if color == 1 else 'W'
        if not has_liberty(board, x, y, own_char):
            return False, f"self_capture_at_{x}_{y}"
    
    return True, "ok"


def has_liberty(board, x, y, color_char):
    visited = set()
    stack = [(x, y)]
    while stack:
        cx, cy = stack.pop()
        if (cx, cy) in visited:
            continue
        if board[cy][cx] == '.':
            return True
        if board[cy][cx] != color_char:
            continue
        visited.add((cx, cy))
        for dx, dy in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
            nx, ny = cx + dx, cy + dy
            if 0 <= nx < BOARD_13 and 0 <= ny < BOARD_13:
                if (nx, ny) not in visited:
                    stack.append((nx, ny))
    return False


def remove_group(board, x, y, color_char):
    if board[y][x] != color_char:
        return
    visited = set()
    stack = [(x, y)]
    while stack:
        cx, cy = stack.pop()
        if (cx, cy) in visited:
            continue
        if board[cy][cx] != color_char:
            continue
        visited.add((cx, cy))
        board[cy][cx] = '.'
        for dx, dy in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
            nx, ny = cx + dx, cy + dy
            if 0 <= nx < BOARD_13 and 0 <= ny < BOARD_13:
                if (nx, ny) not in visited:
                    stack.append((nx, ny))


def main():
    print("Loading original 19x19 problems...")
    with open('original_19x19.json', 'r') as f:
        problems = json.load(f)
    print(f"Total: {len(problems)}")
    
    # 过滤题库
    filtered = [p for p in problems if p.get('book', '') not in BOOKS_TO_DELETE]
    print(f"After book filter: {len(filtered)}")
    
    # 过滤小题库
    from collections import Counter
    book_counts = Counter(p.get('book', '') for p in filtered)
    valid_books = {b for b, c in book_counts.items() if c > 30}
    final = [p for p in filtered if p.get('book', '') in valid_books]
    print(f"After small book filter: {len(final)}")
    
    # 转换
    print("\nConverting (pure translation, no flipping)...")
    converted = []
    stats = Counter()
    edge_stats = Counter()
    
    for p in final:
        new_p, status = convert_problem(p)
        stats[status] += 1
        if new_p is None:
            continue
        
        # 统计边线
        all_pts = [(s[0], s[1]) for s in new_p['stones']] + [tuple(new_p['answer'])]
        for m in new_p['solutionMoves']:
            all_pts.append((m[0], m[1]))
        if any(pt[0] == 0 for pt in all_pts): edge_stats['left'] += 1
        if any(pt[0] == MAX_COORD_13 for pt in all_pts): edge_stats['right'] += 1
        if any(pt[1] == 0 for pt in all_pts): edge_stats['top'] += 1
        if any(pt[1] == MAX_COORD_13 for pt in all_pts): edge_stats['bottom'] += 1
        
        converted.append(new_p)
    
    print(f"Converted: {len(converted)}")
    for status, count in stats.most_common():
        print(f"  {status}: {count}")
    print(f"Edge stats: {dict(edge_stats)}")
    
    # 验证
    print("\nVerifying...")
    valid = []
    invalid_reasons = Counter()
    for p in converted:
        ok, reason = simulate_and_verify(p)
        if ok:
            valid.append(p)
        else:
            invalid_reasons[reason] += 1
    
    print(f"Valid: {len(valid)}")
    print(f"Invalid: {len(converted) - len(valid)}")
    for reason, count in invalid_reasons.most_common(5):
        print(f"  {reason}: {count}")
    
    # 题库分布
    print("\nBook distribution:")
    book_dist = Counter(p.get('book', '') for p in valid)
    for book, count in book_dist.most_common():
        print(f"  {book}: {count}")
    
    # 保存
    with open('app/src/main/assets/problems_full.json', 'w', encoding='utf-8') as f:
        json.dump(valid, f, ensure_ascii=False, separators=(',', ':'))
    
    json_str = json.dumps(valid, ensure_ascii=False, separators=(',', ':'))
    compressed = zlib.compress(json_str.encode('utf-8'))
    with open('app/src/main/assets/problems_compressed.bin', 'wb') as f:
        f.write(compressed)
    
    print(f"\nSaved {len(valid)} problems")
    print(f"JSON: {len(json_str)//1024}KB, Compressed: {len(compressed)//1024}KB")
    
    # 抽样验证
    print("\n=== Spot check ===")
    for p in valid[:3]:
        print(f"\n{p['title']} (book: {p.get('book','')})")
        board = [['.' for _ in range(BOARD_13)] for _ in range(BOARD_13)]
        for s in p['stones']:
            board[s[1]][s[0]] = 'B' if s[2] == 1 else 'W'
        ax, ay = p['answer']
        board[ay][ax] = 'A'
        for y in range(BOARD_13):
            row = ''.join(board[y])
            if row.strip('.'):
                print(f"  {y:2d}: {row}")


if __name__ == '__main__':
    main()
