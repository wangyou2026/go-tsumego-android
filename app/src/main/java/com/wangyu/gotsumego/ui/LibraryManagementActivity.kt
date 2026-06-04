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
import java.io.InputStreamReader

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
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_IMPORT_SGF)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_SGF && resultCode == RESULT_OK) {
            data?.data?.let { uri -> importSgfFile(uri) }
        }
    }
    
    private fun importSgfFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val sgfText = reader.readText()
            reader.close()
            
            val problems = parseSgfToProblems(sgfText)
            if (problems.isEmpty()) {
                Toast.makeText(this, "未解析到有效题目", Toast.LENGTH_SHORT).show()
                return
            }
            repository.addUserProblems(problems)
            Toast.makeText(this, "成功导入 ${problems.size} 道题", Toast.LENGTH_SHORT).show()
            refreshProblemList()
        } catch (e: Exception) {
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
            .setView(input, 32, 16, 32, 16)
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
        
        // Override the book name with user's title
        val namedProblems = problems.map { it.copy(book = title) }
        repository.addUserProblems(namedProblems)
        Toast.makeText(this, "成功添加 ${namedProblems.size} 道题", Toast.LENGTH_SHORT).show()
        binding.etTitle.text?.clear()
        binding.etSgfText.text?.clear()
        refreshProblemList()
    }
    
    /**
     * Parse SGF text into Problem objects
     */
    private fun parseSgfToProblems(sgfText: String): List<Problem> {
        val problems = mutableListOf<Problem>()
        val bookName = detectBookName(sgfText)
        
        // Split into individual SGF games if multiple
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
        
        if (games.isEmpty() && text.contains("AB") || text.contains("AW")) {
            games.add(text)
        }
        return games
    }
    
    private fun parseSingleSgf(sgf: String, defaultBook: String): Problem? {
        // Extract board setup
        val stones = mutableListOf<Stone>()
        var toPlay = StoneColor.BLACK
        val solutionRows = mutableListOf<SolutionMove>()
        
        // Parse AB (Black stones)
        val abPattern = Regex("AB\[([a-z]+)\]", RegexOption.IGNORE_CASE)
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
        
        // Parse AW (White stones)
        val awPattern = Regex("AW\[([a-z]+)\]", RegexOption.IGNORE_CASE)
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
        
        // Parse PL (Player to play)
        val plPattern = Regex("PL\[([BWbw])\]", RegexOption.IGNORE_CASE)
        val plMatch = plPattern.find(sgf)
        toPlay = if (plMatch?.groupValues?.get(1)?.uppercase() == "W") StoneColor.WHITE else StoneColor.BLACK
        
        // Parse solution from variation tree (the main line after the initial position)
        // Look for ;B[xx] or ;W[xx] after the root node
        val movePattern = Regex(";([BWbw])\[([a-z]+)\]")
        var foundSetup = false
        for (match in movePattern.findAll(sgf)) {
            val color = match.groupValues[1].uppercase()
            val coord = match.groupValues[2]
            if (!foundSetup) {
                // Skip the first B/W that might be part of setup
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
        
        // If no solution found in main line, try the first variation line
        if (solutionRows.isEmpty()) {
            // Look for moves inside variations
            val varPattern = Regex("\(;[BWbw]\[([a-z]+)\]")
            for (match in varPattern.findAll(sgf)) {
                val coord = match.groupValues[1]
                if (coord.length >= 2) {
                    val col = coord[0] - 'a'
                    val row = coord[1] - 'a'
                    if (col in 0..12 && row in 0..12) {
                        // Determine color - the move should be the opposite of toPlay for first move in variation
                        val parenthetical = match.value
                        val colorChar = parenthetical[2]
                        val stoneColor = if (colorChar == 'B' || colorChar == 'b') StoneColor.BLACK else StoneColor.WHITE
                        solutionRows.add(SolutionMove(col, row, stoneColor))
                        break // Only take first move of first variation
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
        // Try to extract GN (Game Name) or SZ (Size) or user comment
        val namePattern = Regex("GN\[([^\]]+)\]", RegexOption.IGNORE_CASE)
        val nameMatch = namePattern.find(sgfText)
        return nameMatch?.groupValues?.get(1)?.take(20) ?: "导入题目"
    }
    
    private fun refreshProblemList() {
        val userProblems = repository.getUserProblems()
        binding.rvUserProblems.layoutManager = LinearLayoutManager(this)
        binding.rvUserProblems.adapter = UserProblemAdapter(userProblems) { problem ->
            // Show delete confirmation
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
