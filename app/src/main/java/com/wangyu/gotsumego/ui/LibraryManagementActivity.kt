package com.wangyu.gotsumego.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.TsumegoApp
import com.wangyu.gotsumego.data.*
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import kotlin.math.abs

class LibraryManagementActivity : AppCompatActivity() {
    private val repository by lazy { TsumegoApp.instance.repository }

    private lateinit var rvBooks: RecyclerView
    private lateinit var btnNewBook: com.google.android.material.button.MaterialButton
    private lateinit var btnImportSgf: com.google.android.material.button.MaterialButton
    private lateinit var btnPasteSgf: com.google.android.material.button.MaterialButton
    private lateinit var btnAddManual: com.google.android.material.button.MaterialButton
    private lateinit var btnBack: android.widget.ImageButton

    private var selectedBook: String? = null
    private lateinit var bookAdapter: BookAdapter

    companion object {
        private const val REQUEST_IMPORT_SGF = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library_management)

        btnBack = findViewById(R.id.btnBack)
        btnNewBook = findViewById(R.id.btnNewBook)
        rvBooks = findViewById(R.id.rvBooks)
        btnImportSgf = findViewById(R.id.btnImportSgf)
        btnPasteSgf = findViewById(R.id.btnPasteSgf)
        btnAddManual = findViewById(R.id.btnAddManual)

        btnBack.setOnClickListener { finish() }
        btnNewBook.setOnClickListener { showNewBookDialog() }
        btnImportSgf.setOnClickListener { startImport() }
        btnPasteSgf.setOnClickListener { showPasteDialog() }
        btnAddManual.setOnClickListener { showManualAddDialog() }

        bookAdapter = BookAdapter(
            onSelect = { book -> selectedBook = book; bookAdapter.setSelected(book) },
            onDelete = { book -> confirmDeleteBook(book) }
        )
        rvBooks.layoutManager = LinearLayoutManager(this)
        rvBooks.adapter = bookAdapter

        refreshBookList()
    }

    private fun refreshBookList() {
        val books = repository.getUserBookNames()
        bookAdapter.setBooks(books)
        if (selectedBook != null && !books.contains(selectedBook)) {
            selectedBook = null
        }
        if (selectedBook == null && books.isNotEmpty()) {
            selectedBook = books.first()
            bookAdapter.setSelected(selectedBook!!)
        }
    }

    private fun showNewBookDialog() {
        val input = EditText(this).apply {
            setHint("输入题库名称")
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            setBackgroundColor(android.graphics.Color.parseColor("#2A2A40"))
            setPadding(16, 16, 16, 16)
            textSize = 14f
        }

        AlertDialog.Builder(this)
            .setTitle("新建题库")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // Create an empty book by adding a dummy and immediately deleting it? No.
                // Just select it - actual problems will be added on import.
                selectedBook = name
                refreshBookList()
                // Ensure this book appears in the list even if empty
                bookAdapter.addBook(name)
                bookAdapter.setSelected(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteBook(book: String) {
        val count = repository.getUserProblemsByBook(book).size
        AlertDialog.Builder(this)
            .setTitle("删除题库")
            .setMessage("确定删除「$book」吗？该题库下 $count 道题将被永久删除。")
            .setPositiveButton("删除") { _, _ ->
                repository.removeUserBook(book)
                if (selectedBook == book) selectedBook = null
                refreshBookList()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startImport() {
        val targetBook = selectedBook
        if (targetBook == null) {
            Toast.makeText(this, "请先新建或选择一个题库", Toast.LENGTH_SHORT).show()
            return
        }
        pickSgfFiles()
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
            val targetBook = selectedBook
            if (targetBook == null) {
                Toast.makeText(this, "请先选择题库", Toast.LENGTH_SHORT).show()
                return
            }

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
                val result = importFile(uri, targetBook)
                totalImported += result.first
                totalErrors += result.second
            }

            val msg = buildString {
                append("导入完成：$totalImported 道题 →「$targetBook」")
                if (totalErrors > 0) append("，$totalErrors 个文件解析失败")
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            refreshBookList()
        }
    }

    private fun importFile(uri: Uri, bookName: String): Pair<Int, Int> {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return Pair(0, 1)
            if (isZipFile(inputStream)) {
                inputStream.close()
                importZipFile(uri, bookName)
            } else {
                inputStream.close()
                importSgfFile(uri, bookName)
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

    private fun importSgfFile(uri: Uri, bookName: String): Pair<Int, Int> {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return Pair(0, 1)
            val bytes = inputStream.readBytes()
            inputStream.close()
            val sgfText = decodeSgfBytes(bytes)

            val problems = parseSgfToProblems(sgfText, bookName)
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

    private fun importZipFile(uri: Uri, bookName: String): Pair<Int, Int> {
        var total = 0
        var errors = 0
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return Pair(0, 1)
            val zis = ZipInputStream(inputStream)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".sgf")) {
                    try {
                        val sgfBytes = zis.readBytes()
                        val sgfText = decodeSgfBytes(sgfBytes)
                        val problems = parseSgfToProblems(sgfText, bookName)
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
        val targetBook = selectedBook
        if (targetBook == null) {
            Toast.makeText(this, "请先新建或选择一个题库", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
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
            .setTitle("粘贴SGF →「$targetBook」")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "SGF内容不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val problems = parseSgfToProblems(text, targetBook)
                if (problems.isEmpty()) {
                    Toast.makeText(this, "未解析到有效题目", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repository.addUserProblems(problems)
                Toast.makeText(this, "成功导入 ${problems.size} 道题 →「$targetBook」", Toast.LENGTH_SHORT).show()
                refreshBookList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showManualAddDialog() {
        val targetBook = selectedBook
        if (targetBook == null) {
            Toast.makeText(this, "请先新建或选择一个题库", Toast.LENGTH_SHORT).show()
            return
        }

        val layout = LayoutInflater.from(this).inflate(R.layout.dialog_manual_add, null)
        val etTitle = layout.findViewById<EditText>(R.id.etTitle)
        val etSgf = layout.findViewById<EditText>(R.id.etSgf)

        AlertDialog.Builder(this)
            .setTitle("手动添加 →「$targetBook」")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val title = etTitle.text.toString().trim()
                val sgfText = etSgf.text.toString().trim()

                if (title.isEmpty() || sgfText.isEmpty()) {
                    Toast.makeText(this, "请填写题目名称和SGF内容", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val problems = parseSgfToProblems(sgfText, targetBook)
                if (problems.isEmpty()) {
                    Toast.makeText(this, "SGF格式有误，请检查后重试", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val namedProblems = problems.map { it.copy(title = title, book = targetBook) }
                repository.addUserProblems(namedProblems)
                Toast.makeText(this, "成功添加 ${namedProblems.size} 道题", Toast.LENGTH_SHORT).show()
                refreshBookList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============================================================
    //  SGF 解析器
    // ============================================================

    private fun parseSgfToProblems(sgfText: String, bookName: String): List<Problem> {
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

        if (games.isEmpty() && (text.contains("AB") || text.contains("AW"))) {
            games.add(text)
        }
        return games
    }

    private fun extractMainLine(sgf: String): String {
        data class ExtractConfig(val maxDepth: Int, val includeFirstVar: Boolean)

        fun extract(config: ExtractConfig): String {
            val result = StringBuilder()
            var depth = 0
            var inBracket = 0

            var firstVarStarted = false
            var collectingFirstVar = false
            val firstVarBuf = StringBuilder()

            fun inFirstVar(): Boolean = config.includeFirstVar && collectingFirstVar

            for (c in sgf) {
                when {
                    c == '[' -> {
                        inBracket++
                        if (depth <= config.maxDepth && !inFirstVar()) result.append(c)
                        if (inFirstVar()) firstVarBuf.append(c)
                    }
                    c == ']' -> {
                        inBracket--
                        if (depth <= config.maxDepth && !inFirstVar()) result.append(c)
                        if (inFirstVar()) firstVarBuf.append(c)
                    }
                    c == '(' && inBracket == 0 -> {
                        depth++
                        if (config.includeFirstVar && depth == 2 && !firstVarStarted) {
                            firstVarStarted = true
                            collectingFirstVar = true
                            firstVarBuf.clear()
                        }
                    }
                    c == ')' && inBracket == 0 -> {
                        if (config.includeFirstVar && depth == 2 && collectingFirstVar) {
                            collectingFirstVar = false
                            result.append(firstVarBuf)
                        }
                        if (depth > 0) depth--
                    }
                    else -> {
                        if (depth <= config.maxDepth && !inFirstVar()) result.append(c)
                        if (inFirstVar()) firstVarBuf.append(c)
                    }
                }
            }
            return result.toString().trim()
        }

        val pass1 = extract(ExtractConfig(maxDepth = 1, includeFirstVar = false))
        val hasMoves = Regex(";([BWbw])\\[[a-z]+\\]").containsMatchIn(pass1)

        if (hasMoves) return pass1

        return extract(ExtractConfig(maxDepth = 1, includeFirstVar = true))
    }

    private fun extractProperties(nodeText: String): Map<String, List<String>> {
        val props = mutableMapOf<String, MutableList<String>>()
        val propPattern = Regex("([A-Z]+)((?:\\[[^\\]]*\\])+)")
        for (match in propPattern.findAll(nodeText)) {
            val key = match.groupValues[1]
            val values = Regex("\\[([^\\]]*)\\]").findAll(match.groupValues[2])
                .map { it.groupValues[1] }.toList()
            props.getOrPut(key) { mutableListOf() }.addAll(values)
        }
        return props
    }

    private fun parseSingleSgf(sgf: String, defaultBook: String): Problem? {
        val mainLine = extractMainLine(sgf)
        if (mainLine.isBlank()) return null

        val rootStart = mainLine.indexOf(';')
        if (rootStart < 0) return null
        val rootContent = mainLine.substring(rootStart)

        val props = extractProperties(rootContent)

        val boardSize = props["SZ"]?.firstOrNull()?.toIntOrNull() ?: 13
        val maxCoord = boardSize - 1
        if (boardSize > 19 || boardSize < 9) return null

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

        val toPlay = if (props["PL"]?.firstOrNull()?.uppercase() == "W") StoneColor.WHITE else StoneColor.BLACK

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

        // 19x19 → 13x13 坐标转换
        val displaySize = 13
        val (finalStones, finalSolutionRows) = if (boardSize > displaySize) {
            val allCoords = mutableListOf<Pair<Int, Int>>()
            for (s in stones) allCoords.add(Pair(s.col, s.row))
            for (m in solutionRows) allCoords.add(Pair(m.col, m.row))

            if (allCoords.isEmpty()) {
                Pair(stones, solutionRows)
            } else {
                val minCol = allCoords.minOf { it.first }
                val minRow = allCoords.minOf { it.second }
                val maxCol = allCoords.maxOf { it.first }
                val maxRow = allCoords.maxOf { it.second }
                val rangeCol = maxCol - minCol
                val rangeRow = maxRow - minRow

                if (rangeCol < displaySize && rangeRow < displaySize) {
                    val padX = (displaySize - 1 - rangeCol) / 2
                    val padY = (displaySize - 1 - rangeRow) / 2
                    val offsetX = minCol - padX
                    val offsetY = minRow - padY

                    val ns = stones.map { Stone(it.col - offsetX, it.row - offsetY, it.color) }
                    val nm = solutionRows.map { SolutionMove(it.col - offsetX, it.row - offsetY, it.color) }
                    Pair(ns, nm)
                } else {
                    val centerX = (minCol + maxCol) / 2
                    val centerY = (minRow + maxRow) / 2
                    val offsetX = maxOf(0, centerX - 6)
                    val offsetY = maxOf(0, centerY - 6)

                    val ns = stones.mapNotNull { s ->
                        val nc = s.col - offsetX
                        val nr = s.row - offsetY
                        if (nc in 0 until displaySize && nr in 0 until displaySize)
                            Stone(nc, nr, s.color) else null
                    }
                    val nm = solutionRows.mapNotNull { m ->
                        val nc = m.col - offsetX
                        val nr = m.row - offsetY
                        if (nc in 0 until displaySize && nr in 0 until displaySize)
                            SolutionMove(nc, nr, m.color) else null
                    }
                    Pair(ns, nm)
                }
            }
        } else {
            Pair(stones, solutionRows)
        }

        val id = abs(sgf.hashCode()) and 0x7FFFFFFF

        return Problem(
            id = id,
            type = ProblemType.LIFE_DEATH,
            difficulty = 1,
            title = defaultBook,
            boardSize = displaySize,
            stones = finalStones,
            toPlay = toPlay,
            correctMoves = finalSolutionRows.take(1).map { Position(it.col, it.row) },
            solutionMoves = finalSolutionRows,
            hint = null,
            solutionComment = null,
            book = defaultBook
        )
    }

    private fun decodeSgfBytes(bytes: ByteArray): String {
        val encodings = listOf(Charsets.UTF_8, Charsets.ISO_8859_1, Charset.forName("GBK"), Charset.forName("Shift_JIS"))
        for (charset in encodings) {
            try {
                val text = String(bytes, charset)
                if (text.contains(';') && (text.contains("AB[") || text.contains("AW["))) {
                    return text
                }
            } catch (_: Exception) { }
        }
        return String(bytes, Charsets.UTF_8)
    }

    // ============================================================
    //  Book Adapter
    // ============================================================

    class BookAdapter(
        private val onSelect: (String) -> Unit,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<BookAdapter.ViewHolder>() {

        private var books: List<String> = emptyList()
        private var selected: String? = null

        fun setBooks(list: List<String>) {
            books = list
            notifyDataSetChanged()
        }

        fun addBook(name: String) {
            if (!books.contains(name)) {
                books = (books + name).sorted()
                notifyDataSetChanged()
            }
        }

        fun setSelected(book: String) {
            selected = book
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_book_manage, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val book = books[position]
            val isSelected = book == selected
            holder.tvName.text = book
            holder.vSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.itemView.isSelected = isSelected
            holder.itemView.setOnClickListener { onSelect(book) }
            holder.itemView.setOnLongClickListener {
                onDelete(book)
                true
            }
        }

        override fun getItemCount() = books.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvBookName)
            val vSelected: View = view.findViewById(R.id.vSelectedIndicator)
        }
    }
}
