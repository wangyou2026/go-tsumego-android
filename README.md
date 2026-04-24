# GoTsumego - 围棋死活题练习 App

## 项目概述

这是一个参考101围棋网风格开发的Android围棋做题App，支持9路、13路、19路棋盘。

## 技术栈

- **语言**: Kotlin
- **架构**: MVVM
- **UI**: Material Design 3
- **最低SDK**: Android 7.0 (API 24)
- **目标SDK**: Android 14 (API 34)

## 项目结构

```
go-tsumego-v2/
├── app/
│   ├── src/main/
│   │   ├── java/com/wangyu/gotsumego/
│   │   │   ├── TsumegoApp.kt           # Application类
│   │   │   ├── data/
│   │   │   │   ├── Problem.kt         # 题目数据模型（核心坐标转换）
│   │   │   │   ├── JsonProblem.kt     # JSON数据模型
│   │   │   │   └── ProblemRepository.kt # 数据仓库
│   │   │   ├── ui/
│   │   │   │   ├── BoardView.kt        # 棋盘绘制组件
│   │   │   │   ├── MainActivity.kt    # 首页
│   │   │   │   └── ProblemActivity.kt # 做题界面
│   │   │   └── util/
│   │   │       └── GoBoard.kt         # 棋盘工具类
│   │   ├── res/                        # 资源文件
│   │   └── assets/
│   │       └── problems_full.json      # 题库
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 核心坐标系统

### JSON格式说明
```json
{
  "stones": [[x, y, color], ...],
  "answer": [x, y]
}
```
- `x` = 列(col)，0-based，从左到右
- `y` = 行(row)，0-based，从上到下
- `color`: 1=黑, 2=白

### 坐标转换
- **Position转Index**: `index = row * boardSize + col` (即 `index = y * boardSize + x`)
- **Index转Position**: `row = index / boardSize`, `col = index % boardSize`

### 棋盘字符串
- 长度 = `boardSize * boardSize`
- `'.'` = 空位
- `'X'` = 黑子
- `'O'` = 白子

## 主要功能

1. **题目分类浏览**
   - 按类型：死活题、手筋题、官子题、吃子题
   - 按难度：1-?级难度递进

2. **棋盘显示**
   - 支持9路、13路、19路棋盘
   - 木质背景、星位标记
   - 黑子白子清晰区分

3. **做题功能**
   - 触摸落子
   - 正解判断
   - 错误提示
   - 提示功能

4. **题目导航**
   - 上一题/下一题
   - 重置题目

## 编译运行

1. 解压项目
2. 在Android Studio中打开
3. 等待Gradle同步完成
4. 运行到设备/模拟器

## 依赖

- AndroidX Core KTX
- Material Components
- Gson (JSON解析)
- ViewBinding
- RecyclerView
- CardView

## 注意事项

1. 题库文件 `problems_full.json` 位于 `app/src/main/assets/`
2. 确保使用正确的坐标转换逻辑
3. 棋盘显示时row=0在顶部，col=0在左边
