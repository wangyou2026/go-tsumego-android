#!/usr/bin/env python3
"""
处理围棋死活题数据：
1. 删除指定题库（其他、官子谱、玄玄棋经、仙机武库、郑）
2. 删除≤30题的题库
3. 转换为13路棋盘坐标
4. 验证答案正确性
5. 生成新的JSON
"""

import json
from collections import defaultdict

def load_data():
    with open('app/src/main/assets/problems_full.json', 'r') as f:
        return json.load(f)

def get_book_counts(data):
    """统计每个题库的题目数量"""
    counts = defaultdict(int)
    for p in data:
        counts[p.get('book', 'unknown')] += 1
    return counts

def needs_answer_y_flip(problem, board_size=19):
    """
    自动检测answer/solutionMoves的y坐标是否需要翻转
    与Problem.kt中的needsAnswerYFlip逻辑一致
    """
    stones = problem.get('stones', [])
    answer = problem.get('answer', [])
    
    if len(answer) < 2 or not stones:
        return False
    
    # 构建棋盘（stones始终y翻转）
    board = [['.' for _ in range(board_size)] for _ in range(board_size)]
    stone_rows = []
    
    for s in stones:
        if len(s) >= 3:
            col = s[0]
            row = board_size - 1 - s[1]  # stones y翻转
            symbol = 'X' if s[2] == 1 else 'O'
            if 0 <= row < board_size and 0 <= col < board_size:
                board[row][col] = symbol
                stone_rows.append(row)
    
    ans_col = answer[0]
    ans_row_no_flip = answer[1]
    ans_row_flip = board_size - 1 - answer[1]
    
    idx_no_flip = ans_row_no_flip * board_size + ans_col
    idx_flip = ans_row_flip * board_size + ans_col
    
    no_flip_empty = idx_no_flip < board_size * board_size and board[ans_row_no_flip][ans_col] == '.'
    flip_empty = idx_flip < board_size * board_size and board[ans_row_flip][ans_col] == '.'
    
    # 只有一个方向是空位
    if no_flip_empty and not flip_empty:
        return False
    if flip_empty and not no_flip_empty:
        return True
    
    # 两个方向都是空位，看哪个更靠近棋子
    if no_flip_empty and flip_empty and stone_rows:
        mid_row = (min(stone_rows) + max(stone_rows)) / 2.0
        dist_no_flip = abs(ans_row_no_flip - mid_row)
        dist_flip = abs(ans_row_flip - mid_row)
        return dist_flip < dist_no_flip
    
    # 默认不翻转
    return False

def convert_coordinates(problem, board_size=19):
    """
    转换坐标到13路棋盘坐标系
    1. stones的y坐标翻转：y=0是底部 → row = boardSize - 1 - y
    2. answer/solutionMoves的y坐标：用needsAnswerYFlip()检测
    3. 找到最小包围框
    4. 平移使min_x=0, min_y=0
    5. 检查是否在13路范围内（max_x ≤ 12, max_y ≤ 12）
    """
    stones = problem.get('stones', [])
    answer = problem.get('answer', [])
    solution_moves = problem.get('solutionMoves', [])
    
    # 检测answer是否需要翻转
    flip_answer_y = needs_answer_y_flip(problem, board_size)
    
    # 收集所有转换后的坐标
    all_coords = []
    
    # 转换stones
    converted_stones = []
    for s in stones:
        if len(s) >= 3:
            col = s[0]
            row = board_size - 1 - s[1]  # stones y翻转
            color = s[2]
            converted_stones.append([col, row, color])
            all_coords.append((col, row))
    
    # 转换answer
    converted_answer = []
    if len(answer) >= 2:
        ans_col = answer[0]
        ans_row = board_size - 1 - answer[1] if flip_answer_y else answer[1]
        converted_answer = [ans_col, ans_row]
        all_coords.append((ans_col, ans_row))
    
    # 转换solutionMoves
    converted_solution_moves = []
    for move in solution_moves:
        if len(move) >= 3:
            col = move[0]
            row = board_size - 1 - move[1] if flip_answer_y else move[1]
            color = move[2]
            converted_solution_moves.append([col, row, color])
            all_coords.append((col, row))
    
    if not all_coords:
        return None
    
    # 找到最小包围框
    min_x = min(c[0] for c in all_coords)
    max_x = max(c[0] for c in all_coords)
    min_y = min(c[1] for c in all_coords)
    max_y = max(c[1] for c in all_coords)
    
    # 检查是否在13路范围内
    width = max_x - min_x
    height = max_y - min_y
    
    if width > 12 or height > 12:
        return None
    
    # 平移使min_x=0, min_y=0
    offset_x = min_x
    offset_y = min_y
    
    final_stones = [[s[0] - offset_x, s[1] - offset_y, s[2]] for s in converted_stones]
    final_answer = [converted_answer[0] - offset_x, converted_answer[1] - offset_y] if converted_answer else []
    final_solution_moves = [[m[0] - offset_x, m[1] - offset_y, m[2]] for m in converted_solution_moves]
    
    # 再次验证范围
    all_final_coords = [(c[0], c[1]) for c in final_stones]
    if final_answer:
        all_final_coords.append((final_answer[0], final_answer[1]))
    all_final_coords.extend((m[0], m[1]) for m in final_solution_moves)
    
    final_max_x = max(c[0] for c in all_final_coords)
    final_max_y = max(c[1] for c in all_final_coords)
    
    if final_max_x > 12 or final_max_y > 12:
        return None
    
    return {
        'stones': final_stones,
        'answer': final_answer,
        'solution_moves': final_solution_moves,
        'offset_x': offset_x,
        'offset_y': offset_y,
        'final_max_x': final_max_x,
        'final_max_y': final_max_y
    }

def validate_problem(original_problem, converted):
    """
    验证答案正确性：
    1. 第一步(answer位置)是否在空位上
    2. solutionMoves的连续步骤是否在合理范围内
    """
    if converted is None:
        return False, "转换失败"
    
    stones = converted['stones']
    answer = converted['answer']
    solution_moves = converted['solution_moves']
    
    if not answer:
        return False, "答案为空"
    
    # 构建棋盘
    board = [['.' for _ in range(13)] for _ in range(13)]
    for s in stones:
        col, row, color = s[0], s[1], s[2]
        if 0 <= col < 13 and 0 <= row < 13:
            board[row][col] = 'X' if color == 1 else 'O'
    
    # 检查answer位置是否为空
    ans_col, ans_row = answer[0], answer[1]
    if not (0 <= ans_col < 13 and 0 <= ans_row < 13):
        return False, f"答案位置超出范围: ({ans_col}, {ans_row})"
    if board[ans_row][ans_col] != '.':
        return False, f"答案位置有棋子: ({ans_col}, {ans_row})"
    
    # 模拟solutionMoves
    current_board = [row[:] for row in board]  # 深拷贝
    
    for i, move in enumerate(solution_moves):
        col, row, color = move[0], move[1], move[2]
        if not (0 <= col < 13 and 0 <= row < 13):
            return False, f"第{i+1}步超出范围: ({col}, {row})"
        if current_board[row][col] != '.':
            return False, f"第{i+1}步位置有棋子: ({col}, {row})"
        current_board[row][col] = 'X' if color == 1 else 'O'
    
    return True, "有效"

def process_data():
    print("=" * 60)
    print("围棋死活题数据处理 - 13路棋盘适配")
    print("=" * 60)
    
    # 加载原始数据
    print("\n1. 加载原始数据...")
    original_data = load_data()
    print(f"   原始题目总数: {len(original_data)}")
    
    # 统计原始题库
    original_books = get_book_counts(original_data)
    print(f"   原始题库数: {len(original_books)}")
    
    # Step 1: 删除指定题库
    print("\n2. 删除指定题库（其他、官子谱、玄玄棋经、仙机武库、郑）...")
    delete_books = {'其他', '官子谱', '玄玄棋经', '仙机武库', '郑'}
    deleted_count = sum(original_books.get(book, 0) for book in delete_books)
    filtered_data = [p for p in original_data if p.get('book', '') not in delete_books]
    print(f"   删除题目数: {deleted_count}")
    print(f"   剩余题目数: {len(filtered_data)}")
    
    # 统计删除指定题库后的题库
    after_delete_books = get_book_counts(filtered_data)
    
    # Step 2: 删除≤30题的题库
    print("\n3. 删除≤30题的题库...")
    small_books = {book for book, count in after_delete_books.items() if count <= 30}
    small_count = sum(after_delete_books.get(book, 0) for book in small_books)
    print(f"   将要删除的题库: {small_books}")
    print(f"   删除题目数: {small_count}")
    
    filtered_data = [p for p in filtered_data if p.get('book', '') not in small_books]
    print(f"   剩余题目数: {len(filtered_data)}")
    
    # 统计最终题库
    final_books = get_book_counts(filtered_data)
    print(f"\n   最终题库数: {len(final_books)}")
    
    # Step 3: 13路棋盘适配
    print("\n4. 13路棋盘坐标转换...")
    converted_problems = []
    invalid_reasons = defaultdict(int)
    
    for i, problem in enumerate(filtered_data):
        # 转换坐标
        converted = convert_coordinates(problem)
        
        if converted is None:
            invalid_reasons['超出13路范围'] += 1
            continue
        
        # 验证答案正确性
        is_valid, reason = validate_problem(problem, converted)
        
        if not is_valid:
            invalid_reasons[reason] += 1
            continue
        
        # 构建新题目
        new_problem = {
            'id': problem['id'],
            'type': problem.get('type', 'life_death'),
            'difficulty': problem.get('difficulty', 3),
            'title': problem.get('title', ''),
            'boardSize': 13,
            'stones': converted['stones'],
            'toPlay': problem.get('toPlay', 1),
            'answer': converted['answer'],
            'book': problem.get('book', '其他'),
            'solutionMoves': converted['solution_moves'],
            'solutionComment': problem.get('solutionComment'),
            'hint': problem.get('hint')
        }
        converted_problems.append(new_problem)
        
        if (i + 1) % 2000 == 0:
            print(f"   处理进度: {i+1}/{len(filtered_data)}")
    
    print(f"\n   转换成功: {len(converted_problems)}")
    print(f"   转换失败统计:")
    for reason, count in sorted(invalid_reasons.items(), key=lambda x: -x[1]):
        print(f"     - {reason}: {count}")
    
    # 重新统计转换后的题库
    converted_books = get_book_counts(converted_problems)
    
    # Step 5: 删除≤30题的题库（转换后可能有些题库变少）
    print("\n5. 再次删除≤30题的题库...")
    small_books_after = {book for book, count in converted_books.items() if count <= 30}
    small_count_after = sum(converted_books.get(book, 0) for book in small_books_after)
    print(f"   将要删除的题库: {small_books_after}")
    print(f"   删除题目数: {small_count_after}")
    
    converted_problems = [p for p in converted_problems if p.get('book', '') not in small_books_after]
    print(f"   剩余题目数: {len(converted_problems)}")
    
    # 最终统计
    print("\n" + "=" * 60)
    print("最终结果统计")
    print("=" * 60)
    
    final_book_counts = get_book_counts(converted_problems)
    print("\n题库分布（按题量排序）:")
    for book, count in sorted(final_book_counts.items(), key=lambda x: -x[1]):
        print(f"  {book}: {count}")
    
    print(f"\n总题数: {len(converted_problems)}")
    print(f"总题库数: {len(final_book_counts)}")
    
    # 保存结果
    output_path = 'app/src/main/assets/problems_13x13.json'
    print(f"\n保存到: {output_path}")
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(converted_problems, f, ensure_ascii=False, indent=2)
    
    print("\n保存完成!")
    
    return converted_problems, final_book_counts

def verify_sample_problems(problems):
    """抽查几道题的坐标转换"""
    print("\n" + "=" * 60)
    print("抽查坐标转换验证")
    print("=" * 60)
    
    import random
    samples = random.sample(problems, min(3, len(problems)))
    
    for i, p in enumerate(samples):
        print(f"\n题目 {i+1}: {p.get('title', 'N/A')}")
        print(f"  原题库: {p.get('book', 'N/A')}")
        print(f"  stones (前3个): {p['stones'][:3]}...")
        print(f"  answer: {p.get('answer', [])}")
        print(f"  solutionMoves (前3步): {p.get('solutionMoves', [])[:3]}")
        
        # 验证范围
        all_coords = [(s[0], s[1]) for s in p['stones']]
        if p.get('answer'):
            all_coords.append(tuple(p['answer']))
        all_coords.extend((m[0], m[1]) for m in p.get('solutionMoves', []))
        
        if all_coords:
            xs = [c[0] for c in all_coords]
            ys = [c[1] for c in all_coords]
            print(f"  坐标范围: x=[{min(xs)}, {max(xs)}], y=[{min(ys)}, {max(ys)}]")

if __name__ == '__main__':
    problems, book_counts = process_data()
    verify_sample_problems(problems)
