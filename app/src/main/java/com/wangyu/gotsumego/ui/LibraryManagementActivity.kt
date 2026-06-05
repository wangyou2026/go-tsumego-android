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

            // Handle multiple files
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

    /**
     * 导入单个文件（支持 .sgf 和 .zip）
     * @return Pair(成功题数, 错误文件数)
     */
    private fun importFile(uri: Uri): Pair<Int, Int> {
        return try {
            val fileName = getFileName(uri) ?: "unknown"
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

    private fun getFileName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
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

    private fun parseSgfToProblems(sgfText: String): List<Problem> {
        val problems = mutableListOf<Problem>()
        val bookName = detectBookName(sgfText)
        val gameTexts = splitSgfGames(sgfText)

        for (gameText in gameTexts) {
            try {
                val problem = parseSingleSgf(gameText.trim(), bookName)
                if (problem != null) problems.add(problem)
            } catch (e: Exception) {
                // Skip invalid entries
            }
        }
        return problems
    }

    private fun splitSgfGames(text: String): List<String> {
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

        if (games.isEmpty() && (text.contains("AB") || text.contains("AW"))) {
            games.add(text)
        }
        return games
    }

    private fun parseSingleSgf(sgf: String, defaultBook: String): Problem? {
        val stones = mutableListOf<Stone>()
        var toPlay = StoneColor.BLACK
        val solutionRows = mutableListOf<SolutionMove>()

        val abPattern = Regex("AB\\[([a-z]+)\\]", RegexOption.IGNORE_CASE)
        for (match in abPattern.findAll(sgf)) {
            val coord = match.groupValues[1]
            if (coord.length >= 2) {
                val col = coord[0] - 'a'
                val row = coord[1] - 'a'
                if (col in 0..12 && row in 0..12) {
                    stones.add(Stone(col, row, StoneColor.BLACK))
                }
            }
        }

        val awPattern = Regex("AW\\[([a-z]+)\\]", RegexOption.IGNORE_CASE)
        for (match in awPattern.findAll(sgf)) {
            val coord = match.groupValues[1]
            if (coord.length >= 2) {
                val col = coord[0] - 'a'
                val row = coord[1] - 'a'
                if (col in 0..12 && row in 0..12) {
                    stones.add(Stone(col, row, StoneColor.WHITE))
                }
            }
        }

        if (stones.isEmpty()) return null

        val plPattern = Regex("PL\\[([BWbw])\\]", RegexOption.IGNORE_CASE)
        val plMatch = plPattern.find(sgf)
        toPlay = if (plMatch?.groupValues?.get(1)?.uppercase() == "W") StoneColor.WHITE else StoneColor.BLACK

        val movePattern = Regex(";([BWbw])\\[([a-z]+)\\]")
        var foundSetup = false
        for (match in movePattern.findAll(sgf)) {
            val color = match.groupValues[1].uppercase()
            val coord = match.groupValues[2]
            if (!foundSetup) {
                foundSetup = true
                continue
            }
            if (coord.length >= 2) {
                val col = coord[0] - 'a'
                val row = coord[1] - 'a'
                if (col in 0..12 && row in 0..12) {
                    val stoneColor = if (color == "B") StoneColor.BLACK else StoneColor.WHITE
                    solutionRows.add(SolutionMove(col, row, stoneColor))
                }
            }
        }

        if (solutionRows.isEmpty()) {
            val varPattern = Regex("\\(;[BWbw]\\[([a-z]+)\\]")
            for (match in varPattern.findAll(sgf)) {
                val coord = match.groupValues[1]
                if (coord.length >= 2) {
                    val col = coord[0] - 'a'
                    val row = coord[1] - 'a'
                    if (col in 0..12 && row in 0..12) {
                        val parenthetical = match.value
                        val colorChar = parenthetical[2]
                        val stoneColor = if (colorChar == 'B' || colorChar == 'b') StoneColor.BLACK else StoneColor.WHITE
                        solutionRows.add(SolutionMove(col, row, stoneColor))
                        break
                    }
                }
            }
        }

        val boardSize = 13
        var id = Math.abs(sgf.hashCode())

        return Problem(
            id = id,
            type = ProblemType.LIFE_DEATH,
            difficulty = 1,
            title = defaultBook,
            boardSize = boardSize,
            stones = stones,
            toPlay = toPlay,
            correctMoves = solutionRows.take(1).map { Position(it.col, it.row) },
            solutionMoves = solutionRows,
            hint = null,
            solutionComment = null,
            book = defaultBook
        )
    }

    private fun detectBookName(sgfText: String): String {
        val namePattern = Regex("GN\\[([^\\]]+)\\]", RegexOption.IGNORE_CASE)
        val nameMatch = namePattern.find(sgfText)
        return nameMatch?.groupValues?.get(1)?.take(20) ?: "导入题目"
    }

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