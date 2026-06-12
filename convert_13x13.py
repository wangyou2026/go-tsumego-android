#!/usr/bin/env python3
"""
围棋死活题19路到13路转换脚本
修复边线保留问题
关键：stones不翻转，answer/solutionMoves需要翻转y
"""

import json
import zlib
import copy

# 删除的题库列表
BOOKS_TO_DELETE = ['其他', '官子谱', '玄玄棋经', '仙机武库', '郑']

def flip_y(y, board_size=19):
    """翻转y坐标"""
    return board_size - 1 - y

def process_solution_moves(solution_moves):
    """处理solutionMoves：检测变化图，取第一个"""
    if not solution_moves:
        return solution_moves
    
    # 将连续同色的步作为变化图分割点
    processed = []
    for i, move in enumerate(solution_moves):
        if i == 0:
            processed.append(move)
        else:
            if solution_moves[i-1][2] == move[2]:
                break
            processed.append(move)
    
    # 检查重复
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
    """转换单个题目到13路"""
    board_size = 19
    target_size = 13
    
    # 1. stones不翻转（y=0是底部）
    stones = [s[:] for s in problem['stones']]
    
    # 2. 翻转answer（从顶部坐标系转换到底部坐标系）
    answer = [problem['answer'][0], flip_y(problem['answer'][1])]
    
    # 3. 翻转solutionMoves
    moves = problem.get('solutionMoves', [])
    processed_moves = process_solution_moves(moves)
    flipped_moves = [[m[0], flip_y(m[1]), m[2]] for m in processed_moves]
    
    # 4. 收集所有点（包括answer和flipped_moves）
    all_points = []
    for stone in stones:
        all_points.append((stone[0], stone[1]))
    all_points.append((answer[0], answer[1]))
    for move in flipped_moves:
        all_points.append((move[0], move[1]))
    
    # 5. 计算包围框
    min_x = min(p[0] for p in all_points)
    max_x = max(p[0] for p in all_points)
    min_y = min(p[1] for p in all_points)
    max_y = max(p[1] for p in all_points)
    
    width = max_x - min_x + 1
    height = max_y - min_y + 1
    
    # 6. 检查边线接触（基于底部坐标系）
    touches_right = any(p[0] == 18 for p in all_points)
    touches_bottom = any(p[1] == 18 for p in all_points)
    touches_left = any(p[0] == 0 for p in all_points)
    touches_top = any(p[1] == 0 for p in all_points)
    
    # 7. 检查是否超过13路
    if width > 13 or height > 13:
        return None, "too_large"
    
    # 8. 计算偏移量（边线保留策略）
    # x方向
    if touches_left:
        offset_x = 0
    elif touches_right:
        offset_x = 12 - (max_x - min_x)
    else:
        offset_x = 0
    
    # y方向（底部坐标系）
    if touches_bottom:
        offset_y = 0
    elif touches_top:
        offset_y = 12 - (max_y - min_y)
    else:
        offset_y = 0
    
    # 9. 转换坐标
    def convert_coord(x, y):
        new_x = x - min_x + offset_x
        new_y = y - min_y + offset_y
        return new_x, new_y
    
    # 转换stones
    new_stones = []
    for stone in stones:
        x, y, color = stone
        new_x, new_y = convert_coord(x, y)
        new_stones.append([new_x, new_y, color])
    
    # 转换answer
    new_answer = list(convert_coord(answer[0], answer[1]))
    
    # 转换moves
    new_moves = []
    for move in flipped_moves:
        x, y, color = move
        new_x, new_y = convert_coord(x, y)
        new_moves.append([new_x, new_y, color])
    
    # 10. 验证坐标范围
    for stone in new_stones:
        if not (0 <= stone[0] <= 12 and 0 <= stone[1] <= 12):
            return None, "stone out of range"
    if not (0 <= new_answer[0] <= 12 and 0 <= new_answer[1] <= 12):
        return None, "answer out of range"
    for move in new_moves:
        if not (0 <= move[0] <= 12 and 0 <= move[1] <= 12):
            return None, "move out of range"
    
    # 11. 构建新题目
    new_problem = copy.deepcopy(problem)
    new_problem['boardSize'] = 13
    new_problem['stones'] = new_stones
    new_problem['answer'] = new_answer
    new_problem['solutionMoves'] = new_moves
    
    return new_problem, "ok"

def verify_problem(problem):
    """验证题目"""
    # 检查stones是否有重叠
    stone_set = set()
    for stone in problem['stones']:
        key = (stone[0], stone[1])
        if key in stone_set:
            return False, "stones overlap"
        stone_set.add(key)
    
    # 检查answer是否在空位
    if (problem['answer'][0], problem['answer'][1]) in stone_set:
        return False, "answer on stone"
    
    # 检查solutionMoves第一步是否在空位
    if problem['solutionMoves']:
        first_move = problem['solutionMoves'][0]
        if (first_move[0], first_move[1]) in stone_set:
            return False, "first move on stone"
    
    return True, "ok"

def simulate_and_verify(problem):
    """完整模拟并验证"""
    board = [['.' for _ in range(13)] for _ in range(13)]
    
    # 放置初始棋子
    for stone in problem['stones']:
        x, y, color = stone
        board[y][x] = 'B' if color == 1 else 'W'
    
    # 验证answer在初始状态下是否在空位
    ax, ay = problem['answer']
    if board[ay][ax] != '.':
        return False, f"answer at ({ax},{ay}) not empty"
    
    # 验证第一步与answer一致
    if problem['solutionMoves']:
        first = problem['solutionMoves'][0]
        if first[0] != ax or first[1] != ay:
            return False, "first move != answer"
    
    # 模拟solutionMoves
    for move in problem['solutionMoves']:
        x, y, color = move
        
        # 检查位置是否为空
        if board[y][x] != '.':
            return False, f"move at occupied ({x},{y})"
        
        # 放置棋子
        board[y][x] = 'B' if color == 1 else 'W'
        
        # 提子
        opponent = 2 if color == 1 else 1
        opp_char = 'W' if opponent == 1 else 'B'
        
        for dx, dy in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
            nx, ny = x + dx, y + dy
            if 0 <= nx < 13 and 0 <= ny < 13:
                if board[ny][nx] == opp_char:
                    if not has_liberty(board, nx, ny, opp_char):
                        remove_group(board, nx, ny, opp_char)
    
    return True, "ok"

def has_liberty(board, x, y, color):
    """检查棋子是否有气"""
    visited = set()
    stack = [(x, y)]
    
    while stack:
        cx, cy = stack.pop()
        if (cx, cy) in visited:
            continue
        if board[cy][cx] == '.':
            return True
        if board[cy][cx] != color:
            continue
        
        visited.add((cx, cy))
        for dx, dy in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
            nx, ny = cx + dx, cy + dy
            if 0 <= nx < 13 and 0 <= ny < 13:
                if (nx, ny) not in visited:
                    stack.append((nx, ny))
    
    return False

def remove_group(board, x, y, color):
    """移除一组棋子"""
    target = board[y][x]
    if target != ('B' if color == 1 else 'W'):
        return
    
    visited = set()
    stack = [(x, y)]
    
    while stack:
        cx, cy = stack.pop()
        if (cx, cy) in visited:
            continue
        if board[cy][cx] != target:
            continue
        
        visited.add((cx, cy))
        board[cy][cx] = '.'
        
        for dx, dy in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
            nx, ny = cx + dx, cy + dy
            if 0 <= nx < 13 and 0 <= ny < 13:
                if (nx, ny) not in visited:
                    stack.append((nx, ny))

def main():
    print("Loading original 19x19 problems...")
    with open('original_19x19.json', 'r') as f:
        problems = json.load(f)
    print(f"Total problems: {len(problems)}")
    
    # 过滤题库
    print("\nFiltering by book...")
    filtered = []
    book_counts = {}
    for p in problems:
        book = p.get('book', '')
        if book in BOOKS_TO_DELETE:
            continue
        filtered.append(p)
        book_counts[book] = book_counts.get(book, 0) + 1
    
    print(f"After book filter: {len(filtered)}")
    
    # 过滤题数<=30的题库
    print("\nFiltering books with <= 30 problems...")
    valid_books = {book: count for book, count in book_counts.items() if count > 30}
    final = [p for p in filtered if p.get('book', '') in valid_books]
    print(f"After removing small books: {len(final)}")
    
    # 转换
    print("\nConverting to 13x13...")
    converted = []
    too_large = 0
    convert_errors = 0
    edge_touches = {'right': 0, 'bottom': 0, 'left': 0, 'top': 0}
    edge_both = 0
    
    for i, p in enumerate(final):
        if i % 2000 == 0:
            print(f"  Progress: {i}/{len(final)}")
        
        new_p, status = convert_problem(p)
        if new_p is None:
            convert_errors += 1
            if status == "too_large":
                too_large += 1
            continue
        
        # 统计边线接触
        all_pts = [(s[0], s[1]) for s in new_p['stones']]
        all_pts.append(tuple(new_p['answer']))
        for m in new_p['solutionMoves']:
            all_pts.append((m[0], m[1]))
        
        touches_r = any(p[0] == 12 for p in all_pts)
        touches_b = any(p[1] == 12 for p in all_pts)
        touches_l = any(p[0] == 0 for p in all_pts)
        touches_t = any(p[1] == 0 for p in all_pts)
        
        if touches_r: edge_touches['right'] += 1
        if touches_b: edge_touches['bottom'] += 1
        if touches_l: edge_touches['left'] += 1
        if touches_t: edge_touches['top'] += 1
        if (touches_l and touches_r) or (touches_t and touches_b):
            edge_both += 1
        
        converted.append(new_p)
    
    print(f"Converted: {len(converted)}")
    print(f"Too large (>13x13): {too_large}")
    print(f"Conversion errors: {convert_errors}")
    print(f"Edge touches: {edge_touches}")
    print(f"Touches both sides: {edge_both}")
    
    # 验证
    print("\nVerifying problems...")
    valid = []
    invalid_count = 0
    invalid_reasons = {}
    
    for p in converted:
        ok, reason = simulate_and_verify(p)
        if ok:
            valid.append(p)
        else:
            invalid_count += 1
            invalid_reasons[reason] = invalid_reasons.get(reason, 0) + 1
    
    print(f"Valid: {len(valid)}")
    print(f"Invalid: {invalid_count}")
    for reason, count in sorted(invalid_reasons.items(), key=lambda x: -x[1])[:10]:
        print(f"  {reason}: {count}")
    
    # 统计题库分布
    print("\nProblem distribution by book:")
    book_dist = {}
    for p in valid:
        book = p.get('book', 'unknown')
        book_dist[book] = book_dist.get(book, 0) + 1
    
    for book, count in sorted(book_dist.items(), key=lambda x: -x[1]):
        print(f"  {book}: {count}")
    
    # 保存
    print("\nSaving problems_full.json...")
    with open('problems_full.json', 'w') as f:
        json.dump(valid, f, ensure_ascii=False)
    print(f"Saved {len(valid)} problems")
    
    # 生成compressed.bin
    print("\nGenerating problems_compressed.bin...")
    json_str = json.dumps(valid, ensure_ascii=False)
    compressed = zlib.compress(json_str.encode('utf-8'))
    with open('problems_compressed.bin', 'wb') as f:
        f.write(compressed)
    print(f"Compressed: {len(compressed)} bytes (original: {len(json_str)} bytes)")
    
    # 删除problems_13x13.json如果存在
    import os
    if os.path.exists('problems_13x13.json'):
        os.remove('problems_13x13.json')
        print("Deleted problems_13x13.json")
    
    # 抽样验证边线
    print("\nSampling edge cases for verification...")
    edge_samples = []
    for p in valid:
        all_pts = [(s[0], s[1]) for s in p['stones']]
        all_pts.append(tuple(p['answer']))
        for m in p['solutionMoves']:
            all_pts.append((m[0], m[1]))
        
        touches_r = any(p[0] == 12 for p in all_pts)
        touches_b = any(p[1] == 12 for p in all_pts)
        touches_l = any(p[0] == 0 for p in all_pts)
        touches_t = any(p[1] == 0 for p in all_pts)
        
        if touches_r or touches_b or touches_l or touches_t:
            edge_samples.append({
                'title': p.get('title', ''),
                'book': p.get('book', ''),
                'touches': f"R{touches_r}_B{touches_b}_L{touches_l}_T{touches_t}",
                'stones': p['stones'][:5]
            })
    
    print(f"Total edge cases: {len(edge_samples)}")
    print("\nSample edge cases:")
    for s in edge_samples[:5]:
        print(f"  {s['title']}: {s['touches']}")
        print(f"    Stones: {s['stones']}")
    
    print("\nDone!")
    return valid

if __name__ == '__main__':
    main()
