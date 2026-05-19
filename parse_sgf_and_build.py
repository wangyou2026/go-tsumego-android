#!/usr/bin/env python3
"""
Complete pipeline: Parse SGF files → Generate 19x19 JSON → Convert to 13x13 → Validate

v2: Fixed SGF parser - properly chain sequence nodes and find correct answer variation
"""

import json, zlib, copy, os, re, zipfile
from collections import Counter, defaultdict

###############################################################################
# Part 1: SGF Parser
###############################################################################

class SGFNode:
    """Represents a single SGF node with properties and children."""
    def __init__(self):
        self.properties = defaultdict(list)  # key -> list of values
        self.children = []  # list of SGFNode (first child = continuation of main line)

    def get(self, key, default=None):
        """Get first value of a property."""
        vals = self.properties.get(key, [])
        return vals[0] if vals else default

    def get_all(self, key):
        """Get all values of a property."""
        return self.properties.get(key, [])

    def __repr__(self):
        keys = list(self.properties.keys())
        return f"SGFNode({keys})"


def parse_sgf(text):
    """Parse an SGF string into a list of game trees (each root is an SGFNode)."""
    text = text.strip()
    pos = [0]
    
    def skip_whitespace():
        while pos[0] < len(text) and text[pos[0]] in ' \t\n\r':
            pos[0] += 1
    
    def parse_game_tree():
        """Parse a game tree: (sequence {game_tree})"""
        skip_whitespace()
        if pos[0] >= len(text) or text[pos[0]] != '(':
            return None
        pos[0] += 1  # skip '('
        
        # Parse the sequence of nodes
        nodes = []
        skip_whitespace()
        while pos[0] < len(text) and text[pos[0]] == ';':
            node = parse_node()
            if node:
                nodes.append(node)
            skip_whitespace()
        
        # Parse child game trees (variations)
        child_trees = []
        while pos[0] < len(text) and text[pos[0]] == '(':
            child_tree = parse_game_tree()
            if child_tree:
                child_trees.append(child_tree)
            skip_whitespace()
        
        if pos[0] < len(text) and text[pos[0]] == ')':
            pos[0] += 1  # skip ')'
        
        if not nodes:
            return None
        
        # Chain the sequence nodes together:
        # Node1.children = [Node2], Node2.children = [Node3], ...
        # Last node's children = child_trees (variations)
        for i in range(len(nodes) - 1):
            nodes[i].children = [nodes[i + 1]]
        nodes[-1].children = child_trees
        
        return nodes[0]  # Return root of this tree
    
    def parse_node():
        """Parse a node: ;property[value]..."""
        if pos[0] >= len(text) or text[pos[0]] != ';':
            return None
        pos[0] += 1  # skip ';'
        
        node = SGFNode()
        skip_whitespace()
        
        while pos[0] < len(text) and text[pos[0]] not in ';()':
            # Parse property identifier (uppercase letters)
            prop_start = pos[0]
            while pos[0] < len(text) and text[pos[0]].isalpha() and text[pos[0]].isupper():
                pos[0] += 1
            
            if pos[0] == prop_start:
                # No valid property identifier, skip unexpected character
                if pos[0] < len(text) and text[pos[0]] not in ';()':
                    pos[0] += 1
                skip_whitespace()
                continue
            
            prop_id = text[prop_start:pos[0]]
            skip_whitespace()
            
            # Parse property values: [value]...
            while pos[0] < len(text) and text[pos[0]] == '[':
                pos[0] += 1  # skip '['
                value = parse_value()
                node.properties[prop_id].append(value)
                skip_whitespace()
            
            skip_whitespace()
        
        return node
    
    def parse_value():
        """Parse a property value (everything until unescaped ']')."""
        result = []
        while pos[0] < len(text):
            ch = text[pos[0]]
            if ch == ']':
                pos[0] += 1  # skip ']'
                return ''.join(result)
            elif ch == '\\':
                pos[0] += 1
                if pos[0] < len(text):
                    next_ch = text[pos[0]]
                    if next_ch in '\n\r':
                        pos[0] += 1
                        while pos[0] < len(text) and text[pos[0]] in ' \t\n\r':
                            pos[0] += 1
                        result.append(' ')
                    else:
                        result.append(next_ch)
                        pos[0] += 1
            elif ch == '\t':
                result.append(' ')
                pos[0] += 1
            else:
                result.append(ch)
                pos[0] += 1
        return ''.join(result)
    
    # Parse all game trees
    trees = []
    skip_whitespace()
    while pos[0] < len(text):
        if text[pos[0]] == '(':
            tree = parse_game_tree()
            if tree:
                trees.append(tree)
        else:
            pos[0] += 1
        skip_whitespace()
    
    return trees


###############################################################################
# Part 2: SGF to Problem conversion
###############################################################################

def sgf_coord_to_xy(coord_str):
    """Convert SGF coordinate (e.g., 'aa'-'ss') to (x, y) with top-down y."""
    if not coord_str or len(coord_str) < 2:
        return None
    col_char = coord_str[0]
    row_char = coord_str[1]
    if col_char < 'a' or col_char > 's' or row_char < 'a' or row_char > 's':
        return None
    x = ord(col_char) - ord('a')  # 0-18
    y = ord(row_char) - ord('a')  # 0-18, top-down
    return (x, y)


def extract_main_line_moves(node):
    """
    Extract moves from the main line (first child at each branch point).
    Returns list of (x, y, color) where color is 1 (Black) or 2 (White).
    """
    moves = []
    current = node
    while current:
        for prop_key in ['B', 'W']:
            val = current.get(prop_key)
            if val is not None:
                xy = sgf_coord_to_xy(val)
                if xy:
                    color = 1 if prop_key == 'B' else 2
                    moves.append((xy[0], xy[1], color))
        
        # Follow first child (main line)
        if current.children:
            current = current.children[0]
        else:
            current = None
    
    return moves


def find_correct_answer_node(root):
    """
    Find the correct answer variation from the root node.
    
    Strategy:
    1. Look for a child with N[正解] or N[正解图]
    2. If not found, look for the first child with a B[] or W[] move
    3. Skip children that are only comments (审题, 题目说明 etc.)
    
    Also handles the case where the root node itself has a B[] or W[] move
    (some SGF formats have the first move in the root sequence).
    """
    # Check if root has a move in its sequence (direct continuation)
    current = root
    while current:
        if current.get('B') is not None or current.get('W') is not None:
            return current  # Found a move in the main sequence
        if current.children:
            current = current.children[0]
        else:
            break
    
    # No move in main sequence - check root's direct children for variations
    # First pass: look for 正解/正解图
    for child in root.children:
        name = child.get('N', '')
        if name in ('正解', '正解图'):
            return child
    
    # Second pass: look for first child with a move
    for child in root.children:
        if child.get('B') is not None or child.get('W') is not None:
            return child
    
    # Third pass: check deeper - some children might have moves in their subtree
    for child in root.children:
        moves = extract_main_line_moves(child)
        if moves:
            return child
    
    return None


def sgf_to_problem(sgf_text, book_name, problem_id):
    """
    Parse an SGF file and convert to our problem format.
    
    Coordinate system for 19x19 JSON:
    - stones: [x, y, color] where y = 18 - sgf_y (bottom-up, y=0 is bottom)
    - answer: [x, y] where y = sgf_y (top-down, y=0 is top)
    - solutionMoves: [[x, y, color], ...] where y = sgf_y (top-down)
    """
    try:
        trees = parse_sgf(sgf_text)
    except Exception as e:
        return None, f"parse_error: {e}"
    
    if not trees:
        return None, "no_trees"
    
    root = trees[0]
    
    # Collect ALL AB/AW properties from ALL nodes in the setup phase
    # (before the first move). Some SGF files have AB split across root + setup nodes.
    stones = []
    
    # Walk through the sequence from root, collecting AB/AW until we hit a B/W move
    visited = set()
    queue = [root]
    setup_stones_collected = False
    
    # First, collect setup stones from the root node and its non-move descendants
    current = root
    while current:
        node_id = id(current)
        for coord_str in current.get_all('AB'):
            xy = sgf_coord_to_xy(coord_str)
            if xy:
                stones.append([xy[0], 18 - xy[1], 1])
        for coord_str in current.get_all('AW'):
            xy = sgf_coord_to_xy(coord_str)
            if xy:
                stones.append([xy[0], 18 - xy[1], 2])
        
        # Stop if this node has a move
        if current.get('B') is not None or current.get('W') is not None:
            break
        
        # Continue to first child only (main line setup)
        if current.children:
            # But also check other children for AB/AW (some files put additional setup in variations)
            current = current.children[0]
        else:
            break
    
    # Remove duplicate stones (same position, same color)
    seen = set()
    unique_stones = []
    for s in stones:
        key = (s[0], s[1], s[2])
        if key not in seen:
            seen.add(key)
            unique_stones.append(s)
    stones = unique_stones
    
    if len(stones) < 2:
        return None, f"too_few_stones({len(stones)})"
    
    # Find the correct answer variation
    answer_node = find_correct_answer_node(root)
    if answer_node is None:
        return None, "no_answer_variation"
    
    # Extract all main line moves from the answer node
    moves = extract_main_line_moves(answer_node)
    
    if not moves:
        return None, "no_moves_in_answer"
    
    # Determine toPlay from first move
    first_color = moves[0][2]
    toPlay = first_color
    
    # answer = first move coordinates (top-down y)
    answer = [moves[0][0], moves[0][1]]
    
    # solutionMoves with top-down y
    solutionMoves = [[m[0], m[1], m[2]] for m in moves]
    
    # Extract comments from the answer variation
    solutionComment = None
    current = answer_node
    while current:
        comment = current.get('C')
        name = current.get('N')
        if comment:
            if solutionComment is None:
                solutionComment = comment
            elif comment not in solutionComment:
                solutionComment += " | " + comment
        if current.children:
            current = current.children[0]
        else:
            current = None
    
    # Extract title from GN (game name) or N (name) or C (comment) in root
    title = ""
    gn = root.get('GN')
    if gn:
        title = gn
    else:
        n = root.get('N')
        if n:
            title = n
        else:
            root_comment = root.get('C', '')
            if root_comment:
                title = root_comment.split('\n')[0].strip()[:80]
    
    difficulty = 3
    
    return {
        "id": problem_id,
        "type": "life_death",
        "difficulty": difficulty,
        "title": title,
        "boardSize": 19,
        "stones": stones,
        "toPlay": toPlay,
        "answer": answer,
        "book": book_name,
        "solutionMoves": solutionMoves,
        "solutionComment": solutionComment,
        "hint": None
    }, "ok"


###############################################################################
# Part 3: Book name mapping
###############################################################################

BOOK_NAME_MAP = {
    '前田陈尔': '前田陈尔',
    '吴清源': '吴清源詰棋集',
    '吴清源詰棋集': '吴清源詰棋集',
    '天龙图': '天龙图',
    '棋经众妙': '棋经众妙',
    '死活妙机': '死活妙机',
    '加田克司': '加田克司',
    '官子谱': '官子谱',
    '玄玄棋经': '玄玄棋经',
    '石榑郁郎': '石榑郁郎',
    '郭求真': '郭求真',
    '仙机武库': '仙机武库',
    '山田规三生': '山田规三生',
    '昭和的诘碁': '昭和的诘碁',
    '杨以伦': '杨以伦',
    '发阳论': '发阳论',
    '死活题集锦': '死活题集锦',
    '鬼手魔手': '鬼手魔手',
    '忍耐的算路': '忍耐的算路',
    '石田章': '石田章',
    '石田芳夫': '石田芳夫',
    '福井正义': '福井正义',
    '女流诘碁集': '女流诘碁集',
    '张栩': '张栩',
    '诘棋快乐读本': '诘棋快乐读本',
    '藤泽秀行': '藤泽秀行',
    '忘忧清乐集': '忘忧清乐集',
    '诘碁奇题': '诘碁奇题',
    '尹航': '尹航',
    '珍珑': '珍珑',
    '郑': '郑',
    '围棋死活辞典': '围棋死活辞典',
    '围棋死活词典': '围棋死活辞典',
    '死活大全': '死活大全',
    '棋经精妙': '棋经精妙',
    'TOM高级': 'TOM高级',
    'TOM中级': 'TOM中级',
    'TOM初级': 'TOM初级',
    '赵治勋': '围棋死活辞典',
    '赵和': '昭和的诘碁',
}

def map_dir_to_book(dir_path):
    """Map a directory path to a book name."""
    for pattern, book in BOOK_NAME_MAP.items():
        if pattern in dir_path:
            return book
    return None


###############################################################################
# Part 4: Process all SGF files from zips
###############################################################################

def try_decode_sgf(raw_bytes):
    """Try to decode SGF bytes using various encodings."""
    # Try to detect encoding from CA property first
    try:
        preview = raw_bytes[:200].decode('ascii', errors='replace')
        ca_match = re.search(r'CA\[([^\]]+)\]', preview)
        if ca_match:
            ca_enc = ca_match.group(1).strip().lower()
            enc_map = {
                'gb2312': 'gb2312', 'gbk': 'gbk', 'gb18030': 'gb18030',
                'big5': 'big5', 'shift_jis': 'shift_jis', 'sjis': 'shift_jis',
                'utf-8': 'utf-8', 'euc-jp': 'euc-jp', 'euc-kr': 'euc-kr',
            }
            if ca_enc in enc_map:
                try:
                    return raw_bytes.decode(enc_map[ca_enc])
                except (UnicodeDecodeError, LookupError):
                    pass
    except:
        pass
    
    for enc in ['utf-8', 'gbk', 'gb2312', 'gb18030', 'big5', 'shift_jis', 'cp932', 'euc-jp']:
        try:
            return raw_bytes.decode(enc)
        except (UnicodeDecodeError, LookupError):
            continue
    
    return raw_bytes.decode('utf-8', errors='replace')


def process_zip_file(zip_path, source_name=""):
    """Process all SGF files in a zip file."""
    problems = []
    errors = Counter()
    
    try:
        zf = zipfile.ZipFile(zip_path, 'r')
    except Exception as e:
        print(f"  ERROR: Cannot open {zip_path}: {e}")
        return problems, errors
    
    sgf_names = [n for n in zf.namelist() if n.lower().endswith('.sgf')]
    
    for name in sgf_names:
        # Determine book name from directory path
        try:
            decoded_path = name.encode('cp437').decode('gbk')
        except:
            try:
                decoded_path = name.encode('cp437').decode('gb18030')
            except:
                decoded_path = name
        
        book_name = map_dir_to_book(decoded_path)
        if book_name is None:
            errors['unknown_book'] += 1
            continue
        
        # Read and decode the SGF file
        try:
            raw = zf.read(name)
        except Exception as e:
            errors['read_error'] += 1
            continue
        
        sgf_text = try_decode_sgf(raw)
        
        problem_id = len(problems) + 1
        problem, status = sgf_to_problem(sgf_text, book_name, problem_id)
        
        if problem:
            problems.append(problem)
        else:
            errors[status] += 1
    
    zf.close()
    return problems, errors


###############################################################################
# Part 5: Quality Validation (19x19)
###############################################################################

def validate_problem_19(problem):
    """Validate a 19x19 problem."""
    stones = problem['stones']
    answer = problem['answer']
    moves = problem.get('solutionMoves', [])
    
    if len(stones) < 6:
        return False, "too_few_stones"
    
    # Build board (stones use bottom-up y, convert to top-down for checking)
    board = [[0]*19 for _ in range(19)]
    for s in stones:
        x, y_bottomup, color = s[0], s[1], s[2]
        y_topdown = 18 - y_bottomup
        if 0 <= x <= 18 and 0 <= y_topdown <= 18:
            if board[y_topdown][x] != 0:
                return False, "stone_overlap"
            board[y_topdown][x] = color
        else:
            return False, "stone_out_of_range"
    
    ax, ay = answer[0], answer[1]
    if not (0 <= ax <= 18 and 0 <= ay <= 18):
        return False, "answer_out_of_range"
    if board[ay][ax] != 0:
        return False, "answer_on_stone"
    
    # Answer must be adjacent to at least one stone (Manhattan distance <= 2)
    adjacent = False
    for s in stones:
        sx, sy_bottomup = s[0], s[1]
        sy_topdown = 18 - sy_bottomup
        dist = abs(ax - sx) + abs(ay - sy_topdown)
        if dist <= 2:
            adjacent = True
            break
    if not adjacent:
        return False, "answer_not_adjacent"
    
    # First solution move must match answer
    if moves:
        if moves[0][0] != ax or moves[0][1] != ay:
            return False, "first_move_not_answer"
    
    return True, "ok"


###############################################################################
# Part 6: 13x13 Conversion
###############################################################################

BOARD_19 = 19
BOARD_13 = 13
MAX_19 = 18
MAX_13 = 12

BOOKS_TO_DELETE = ['其他', '官子谱', '玄玄棋经', '仙机武库', '郑']
MIN_BOOK_SIZE = 50


def calc_offset(min_c, max_c, board_from, board_to):
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


def convert_problem_to_13(problem):
    # Unify to top-down coordinate system
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


def get_group(board, x, y, visited=None, board_size=13):
    if visited is None: visited = set()
    if (x,y) in visited: return visited
    if x<0 or x>=board_size or y<0 or y>=board_size: return visited
    c = board[y][x]
    if c==0: return visited
    visited.add((x,y))
    for dx,dy in [(-1,0),(1,0),(0,-1),(0,1)]:
        nx,ny=x+dx,y+dy
        if (nx,ny) not in visited and 0<=nx<board_size and 0<=ny<board_size and board[ny][nx]==c:
            get_group(board, nx, ny, visited)
    return visited


def count_lib(board, group, board_size=13):
    libs = set()
    for x,y in group:
        for dx,dy in [(-1,0),(1,0),(0,-1),(0,1)]:
            nx,ny=x+dx,y+dy
            if 0<=nx<board_size and 0<=ny<board_size and board[ny][nx]==0: libs.add((nx,ny))
    return len(libs)


def place_stone_on_board(board, x, y, color, board_size=13):
    if board[y][x] != 0: return None
    new_board = [row[:] for row in board]
    new_board[y][x] = color
    opp = 3 - color
    for dx, dy in [(-1,0),(1,0),(0,-1),(0,1)]:
        nx, ny = x+dx, y+dy
        if 0<=nx<board_size and 0<=ny<board_size and new_board[ny][nx]==opp:
            g = get_group(new_board, nx, ny, board_size=board_size)
            if count_lib(new_board, g, board_size=board_size)==0:
                for gx,gy in g: new_board[gy][gx]=0
    own = get_group(new_board, x, y, board_size=board_size)
    if count_lib(new_board, own, board_size=board_size)==0: return None
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


###############################################################################
# Part 7: Main Pipeline
###############################################################################

def main():
    base_dir = '/app/data/所有对话/主对话/用户上传'
    
    print("=" * 60)
    print("Step 1: Parse SGF files from 诘棋总动员")
    print("=" * 60)
    
    total_zip = os.path.join(base_dir, '诘棋总动员 SGF 版及答案.zip')
    problems_19, errors_19 = process_zip_file(total_zip, "诘棋总动员")
    
    print(f"  Parsed: {len(problems_19)} problems")
    print(f"  Errors: {dict(errors_19.most_common(10))}")
    
    # Book distribution
    book_counts = Counter(p.get('book','') for p in problems_19)
    print("\n  Book distribution (19x19, before filtering):")
    for book, count in book_counts.most_common():
        print(f"    {book}: {count}")
    
    # Re-number IDs
    for i, p in enumerate(problems_19):
        p['id'] = i + 1
    
    print("\n" + "=" * 60)
    print("Step 2: Validate 19x19 problems")
    print("=" * 60)
    
    valid_19 = []
    invalid_19 = Counter()
    for p in problems_19:
        ok, reason = validate_problem_19(p)
        if ok:
            valid_19.append(p)
        else:
            invalid_19[reason] += 1
    
    print(f"  Valid: {len(valid_19)}")
    print(f"  Invalid: {dict(invalid_19)}")
    
    book_counts_19 = Counter(p.get('book','') for p in valid_19)
    print("\n  Book distribution (19x19, after validation):")
    for book, count in book_counts_19.most_common():
        print(f"    {book}: {count}")
    
    # Save 19x19 JSON
    output_dir = '/app/data/所有对话/主对话/用户上传/go-tsumego-android'
    with open(os.path.join(output_dir, 'original_19x19.json'), 'w', encoding='utf-8') as f:
        json.dump(valid_19, f, ensure_ascii=False, separators=(',', ':'))
    print(f"\n  Saved original_19x19.json: {os.path.getsize(os.path.join(output_dir, 'original_19x19.json'))//1024}KB")
    
    print("\n" + "=" * 60)
    print("Step 3: Filter books and convert to 13x13")
    print("=" * 60)
    
    filtered = [p for p in valid_19 if p.get('book','') not in BOOKS_TO_DELETE]
    book_counts_filtered = Counter(p.get('book','') for p in filtered)
    valid_books = {b for b, c in book_counts_filtered.items() if c >= MIN_BOOK_SIZE}
    final_19 = [p for p in filtered if p.get('book','') in valid_books]
    
    print(f"  After filtering: {len(final_19)} problems")
    print(f"  Books removed (< {MIN_BOOK_SIZE}): {[b for b,c in book_counts_filtered.items() if c < MIN_BOOK_SIZE]}")
    print(f"  Books kept ({len(valid_books)}): {sorted(valid_books)}")
    
    # Convert to 13x13
    converted = []
    conv_stats = Counter()
    for p in final_19:
        new_p, status = convert_problem_to_13(p)
        conv_stats[status] += 1
        if new_p:
            converted.append(new_p)
    
    print(f"  Converted: {len(converted)}")
    print(f"  Conversion failures: {dict(conv_stats)}")
    
    # Verify 13x13
    valid_13 = []
    invalid_13 = Counter()
    for p in converted:
        ok, reason = simulate_and_verify(p)
        if ok:
            valid_13.append(p)
        else:
            invalid_13[reason] += 1
    
    print(f"  Valid 13x13: {len(valid_13)}")
    print(f"  Invalid 13x13: {dict(invalid_13)}")
    
    book_counts_13 = Counter(p.get('book','') for p in valid_13)
    print("\n  Final book distribution (13x13):")
    for book, count in book_counts_13.most_common():
        print(f"    {book}: {count}")
    
    # Re-number IDs
    for i, p in enumerate(valid_13):
        p['id'] = i + 1
    
    print("\n" + "=" * 60)
    print("Step 4: Save results")
    print("=" * 60)
    
    assets_dir = os.path.join(output_dir, 'app', 'src', 'main', 'assets')
    os.makedirs(assets_dir, exist_ok=True)
    
    json_path = os.path.join(assets_dir, 'problems_full.json')
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(valid_13, f, ensure_ascii=False, separators=(',', ':'))
    
    json_str = json.dumps(valid_13, ensure_ascii=False, separators=(',', ':'))
    compressed = zlib.compress(json_str.encode('utf-8'))
    bin_path = os.path.join(assets_dir, 'problems_compressed.bin')
    with open(bin_path, 'wb') as f:
        f.write(compressed)
    
    print(f"  problems_full.json: {os.path.getsize(json_path)//1024}KB")
    print(f"  problems_compressed.bin: {len(compressed)//1024}KB")
    print(f"  Total problems: {len(valid_13)}")
    
    print("\n" + "=" * 60)
    print("Step 5: Quality verification")
    print("=" * 60)
    
    few_stones = sum(1 for p in valid_13 if len(p.get('stones',[])) < 6)
    few_moves = sum(1 for p in valid_13 if len(p.get('solutionMoves',[])) <= 1)
    avg_stones = sum(len(p.get('stones',[])) for p in valid_13) / len(valid_13) if valid_13 else 0
    avg_moves = sum(len(p.get('solutionMoves',[])) for p in valid_13) / len(valid_13) if valid_13 else 0
    
    print(f"  Problems with <6 stones: {few_stones}")
    print(f"  Problems with <=1 move: {few_moves} ({100*few_moves/len(valid_13):.1f}%)")
    print(f"  Average stones: {avg_stones:.1f}")
    print(f"  Average solution moves: {avg_moves:.1f}")
    
    # Move count distribution
    move_dist = Counter()
    for p in valid_13:
        n = len(p.get('solutionMoves', []))
        if n <= 1: move_dist['1'] += 1
        elif n <= 3: move_dist['2-3'] += 1
        elif n <= 5: move_dist['4-5'] += 1
        elif n <= 10: move_dist['6-10'] += 1
        else: move_dist['11+'] += 1
    print(f"  Move count distribution:")
    for k in ['1', '2-3', '4-5', '6-10', '11+']:
        if k in move_dist:
            print(f"    {k}: {move_dist[k]}")
    
    # Sample check per book
    print("\n  Sample problems (first of each book):")
    for book in sorted(book_counts_13.keys()):
        book_probs = [p for p in valid_13 if p.get('book') == book]
        if book_probs:
            p = book_probs[0]
            print(f"    {book}: {len(p['stones'])} stones, {len(p['solutionMoves'])} moves")
    
    return valid_13


if __name__ == '__main__':
    main()
