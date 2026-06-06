package com.wangyu.gotsumego.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.TsumegoApp
import com.wangyu.gotsumego.data.*
import com.wangyu.gotsumego.databinding.ActivityLibraryManagementBinding
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlin.math.abs

class LibraryManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLibraryManagementBinding
    private val repository by lazy { TsumegoApp.instance.repository }

    companion object {
        private const val REQUEST_IMPORT_SGF = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnImportSgf.setOnClickListener { pickSgfFiles() }
        binding.btnPasteSgf.setOnClickListener { showPasteDialog() }
        binding.btnAddManual.setOnClickListener { addFromForm() }

        refreshProblemList()
    }

    private fun pickSgfFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_IMPORT_SGF)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_SGF && resultCode == RESULT_OK) {
            var totalImported = 0
            var totalErrors = 0

            val uris = mutableListOf<Uri>()
            data?.data?.let { uris.add(it) }
            data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i).uri?.let { uris.add(it) }
                }
            }

            if (uris.isEmpty()) {
                Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show()
                return
            }

            for (uri in uris) {
                val result = importFile(uri)
                totalImported += result.first
                totalErrors += result.second
            }

            val msg = buildString {
                append("导入完成：$totalImported 道题")
                if (totalErrors > 0) append("，$totalErrors 个文件解析失败")
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            refreshProblemList()
        }
    }

    private fun importFile(uri: Uri): Pair<Int, Int> {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return Pair(0, 1)
            if (isZipFile(inputStream)) {
                inputStream.close()
                importZipFile(uri)
            } else {
                inputStream.close()
                importSgfFile(uri)
            }
        } catch (e: Exception) {
            Pair(0, 1)
        }
    }

    private fun isZipFile(inputStream: InputStream): Boolean {
        return try {
            val header = ByteArray(4)
            val bytesRead = inputStream.read(header)
            bytesRead >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                    && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
        } catch (e: Exception) {
            false
        }
    }

    private fun importSgfFile(uri: Uri): Pair<Int, Int> {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return Pair(0, 1)
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val sgfText = reader.readText()
            reader.close()

            val problems = parseSgfToProblems(sgfText)
            if (problems.isEmpty()) {
                Pair(0, 1)
            } else {
                repository.addUserProblems(problems)
                Pair(problems.size, 0)
            }
        } catch (e: Exception) {
            Pair(0, 1)
        }
    }

    private fun importZipFile(uri: Uri): Pair<Int, Int> {
        var total = 0
        var errors = 0
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return Pair(0, 1)
            val zis = ZipInputStream(inputStream)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".sgf")) {
                    try {
                        val sgfText = zis.bufferedReader(Charsets.UTF_8).readText()
                        val problems = parseSgfToProblems(sgfText)
                        if (problems.isNotEmpty()) {
                            repository.addUserProblems(problems)
                            total += problems.size
                        } else {
                            errors++
                        }
                    } catch (e: Exception) {
                        errors++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            return Pair(total, errors + 1)
        }
        return Pair(total, errors)
    }

    private fun showPasteDialog() {
        val input = android.widget.EditText(this).apply {
            setHint("在此粘贴SGF文本...")
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            setBackgroundColor(android.graphics.Color.parseColor("#2A2A40"))
            setPadding(16, 16, 16, 16)
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            minLines = 8
            textSize = 12f
        }

        AlertDialog.Builder(this)
            .setTitle("粘贴SGF")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "SGF内容不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val problems = parseSgfToProblems(text)
                if (problems.isEmpty()) {
                    Toast.makeText(this, "未解析到有效题目", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repository.addUserProblems(problems)
                Toast.makeText(this, "成功导入 ${problems.size} 道题", Toast.LENGTH_SHORT).show()
                refreshProblemList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addFromForm() {
        val title = binding.etTitle.text.toString().trim()
        val sgfText = binding.etSgfText.text.toString().trim()

        if (title.isEmpty() || sgfText.isEmpty()) {
            Toast.makeText(this, "请填写题目名称和SGF内容", Toast.LENGTH_SHORT).show()
            return
        }

        val problems = parseSgfToProblems(sgfText)
        if (problems.isEmpty()) {
            Toast.makeText(this, "SGF格式有误，请检查后重试", Toast.LENGTH_SHORT).show()
            return
        }

        val namedProblems = problems.map { it.copy(book = title) }
        repository.addUserProblems(namedProblems)
        Toast.makeText(this, "成功添加 ${namedProblems.size} 道题", Toast.LENGTH_SHORT).show()
        binding.etTitle.text?.clear()
        binding.etSgfText.text?.clear()
        refreshProblemList()
    }

    // ============================================================
    //  SGF 解析器（v2 — 正确提取主线，支持多坐标AB/AW）
    // ============================================================

    /**
     * 解析 SGF 文本为 Problem 列表。
     * 支持多局 SGF（通过顶层括号分割）。
     */
    private fun parseSgfToProblems(sgfText: String): List<Problem> {
        val bookName = detectBookName(sgfText)
        val gameTexts = splitTopLevelGames(sgfText)
        val problems = mutableListOf<Problem>()

        for (gameText in gameTexts) {
            try {
                val problem = parseSingleSgf(gameText.trim(), bookName)
                if (problem != null) problems.add(problem)
            } catch (_: Exception) { }
        }
        return problems
    }

    /**
     * 按顶层括号分割多局 SGF。
     * 仅提取包含 AB 或 AW 的完整 SGF 游戏。
     */
    private fun splitTopLevelGames(text: String): List<String> {
        val games = mutableListOf<String>()
        var depth = 0
        var start = -1

        for (i in text.indices) {
            when (text[i]) {
                '(' -> {
                    if (depth == 0) start = i
                    depth++
                }
                ')' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        val game = text.substring(start, i + 1)
                        if (game.contains("AB") || game.contains("AW")) {
                            games.add(game)
                        }
                        start = -1
                    }
                }
            }
        }

        // 没有顶层括号但有 AB/AW → 整段视为一局
        if (games.isEmpty() && (text.contains("AB") || text.contains("AW"))) {
            games.add(text)
        }
        return games
    }

    /**
     * 从 SGF 中移除所有变化图分支（(…) 对），只保留主线。
     * 外层的 (; ... ) 保留。
     */
    private fun removeVariations(sgf: String): String {
        val result = StringBuilder()
        var depth = 0
        for (c in sgf) {
            when (c) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                else -> if (depth > 0) result.append(c)
            }
        }
        return result.toString().trim()
    }

    /**
     * 从 SGF 节点文本中提取属性键值对。
     * 支持多值属性如 AB[aa][bb] → "AB" -> ["aa", "bb"]
     */
    private fun extractProperties(nodeText: String): Map<String, List<String>> {
        val props = mutableMapOf<String, MutableList<String>>()
        // 匹配 "KEY[...][...]..." 的模式
        val propPattern = Regex("([A-Z]+)((?:\\[[^\\]]*\\])+)")
        for (match in propPattern.findAll(nodeText)) {
            val key = match.groupValues[1]
            val values = Regex("\\[([^\\]]*)\\]").findAll(match.groupValues[2])
                .map { it.groupValues[1] }.toList()
            props.getOrPut(key) { mutableListOf() }.addAll(values)
        }
        return props
    }

    /**
     * 解析单局 SGF，提取棋盘、棋子和正解。
     */
    private fun parseSingleSgf(sgf: String, defaultBook: String): Problem? {
        // 1. 移除所有变化图，只剩主线
        val mainLine = removeVariations(sgf)
        if (mainLine.isBlank()) return null

        // 2. 提取根节点内容（第一个 ; 到字符串结束）
        val rootStart = mainLine.indexOf(';')
        if (rootStart < 0) return null
        val rootContent = mainLine.substring(rootStart)

        // 3. 解析根节点属性
        val props = extractProperties(rootContent)

        // 4. 棋盘大小
        val boardSize = props["SZ"]?.firstOrNull()?.toIntOrNull() ?: 13
        val maxCoord = boardSize - 1
        // 如果棋盘大于 19 路，拒绝（超出合理围棋范围）
        if (boardSize > 19 || boardSize < 9) return null

        // 5. 提取棋子（支持 AB[aa][bb][cc] 多坐标）
        val stones = mutableListOf<Stone>()
        for (coord in props["AB"].orEmpty()) {
            if (coord.length >= 2) {
                val col = coord[0] - 'a'
                val row = coord[1] - 'a'
                if (col in 0..maxCoord && row in 0..maxCoord) {
                    stones.add(Stone(col, row, StoneColor.BLACK))
                }
            }
        }
        for (coord in props["AW"].orEmpty()) {
            if (coord.length >= 2) {
                val col = coord[0] - 'a'
                val row = coord[1] - 'a'
                if (col in 0..maxCoord && row in 0..maxCoord) {
                    stones.add(Stone(col, row, StoneColor.WHITE))
                }
            }
        }
        if (stones.isEmpty()) return null

        // 6. 谁先走
        val toPlay = if (props["PL"]?.firstOrNull()?.uppercase() == "W") StoneColor.WHITE else StoneColor.BLACK

        // 7. 提取正解手数（主线中的所有 ;B/W[coord]）
        val solutionRows = mutableListOf<SolutionMove>()
        val movePattern = Regex(";([BWbw])\\[([a-z]+)\\]")
        for (match in movePattern.findAll(mainLine)) {
            val coord = match.groupValues[2]
            if (coord.length >= 2) {
                val col = coord[0] - 'a'
                val row = coord[1] - 'a'
                if (col in 0..maxCoord && row in 0..maxCoord) {
                    val color = if (match.groupValues[1].uppercase() == "B") StoneColor.BLACK else StoneColor.WHITE
                    solutionRows.add(SolutionMove(col, row, color))
                }
            }
        }

        // 8. 构造 Problem
        // 使用 hash 和位置确保 ID 唯一且非负
        val id = abs(sgf.hashCode()) and 0x7FFFFFFF
        // 显示用的棋盘大小固定为 13（App 只支持 13 路显示）
        val displaySize = 13

        return Problem(
            id = id,
            type = ProblemType.LIFE_DEATH,
            difficulty = 1,
            title = defaultBook,
            boardSize = displaySize,
            stones = stones,
            toPlay = toPlay,
            correctMoves = solutionRows.take(1).map { Position(it.col, it.row) },
            solutionMoves = solutionRows,
            hint = null,
            solutionComment = null,
            book = defaultBook
        )
    }

    /**
     * 从 SGF 中检测题目名称（GN 标签）。
     */
    private fun detectBookName(sgfText: String): String {
        val namePattern = Regex("GN\\[([^\\]]+)\\]", RegexOption.IGNORE_CASE)
        val nameMatch = namePattern.find(sgfText)
        return nameMatch?.groupValues?.get(1)?.take(20) ?: "导入题目"
    }

    // ============================================================
    //  已导入题目列表
    // ============================================================

    private fun refreshProblemList() {
        val userProblems = repository.getUserProblems()
        binding.rvUserProblems.layoutManager = LinearLayoutManager(this)
        binding.rvUserProblems.adapter = UserProblemAdapter(userProblems) { problem ->
            AlertDialog.Builder(this)
                .setTitle("删除题目")
                .setMessage("确定删除「${problem.title}」吗？")
                .setPositiveButton("删除") { _, _ ->
                    repository.removeUserProblem(problem.id)
                    refreshProblemList()
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    class UserProblemAdapter(
        private val problems: List<Problem>,
        private val onLongClick: (Problem) -> Unit
    ) : RecyclerView.Adapter<UserProblemAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvBookName)
            val tvCount: TextView = view.findViewById(R.id.tvBookCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val problem = problems[position]
            holder.tvName.text = problem.title
            holder.tvCount.text = "${problem.solutionMoves.size} 手"
            holder.itemView.setOnLongClickListener {
                onLongClick(problem)
                true
            }
        }

        override fun getItemCount() = problems.size
    }
}