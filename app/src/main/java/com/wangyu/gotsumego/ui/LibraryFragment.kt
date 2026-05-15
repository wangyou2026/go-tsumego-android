package com.wangyu.gotsumego.ui

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.TsumegoApp

class LibraryFragment : Fragment() {
    private val repository by lazy { TsumegoApp.instance.repository }
    companion object { fun newInstance() = LibraryFragment() }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val allProblems = repository.getAllProblems()
        view.findViewById<TextView>(R.id.tvTotalCount).text = "共 ${allProblems.size} 道题目"
        val bookStats = allProblems.groupBy { it.book }.map { (book, problems) -> BookStat(book, problems.size) }.sortedByDescending { it.count }
        val rv = view.findViewById<RecyclerView>(R.id.rvBooks)
        rv.layoutManager = GridLayoutManager(context, 2)
        rv.addItemDecoration(GridSpacingItemDecoration(2, dpToPx(8), true))
        rv.adapter = BookAdapter(bookStats) { bookName ->
            val intent = Intent(requireContext(), ProblemActivity::class.java)
            intent.putExtra(ProblemActivity.EXTRA_BOOK, bookName)
            intent.putExtra(ProblemActivity.EXTRA_TITLE, bookName)
            startActivity(intent)
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacing: Int,
        private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % spanCount
            if (includeEdge) {
                outRect.left = column * spacing / spanCount
                outRect.right = spacing - (column + 1) * spacing / spanCount
                if (position >= spanCount) {
                    outRect.top = spacing
                }
            } else {
                outRect.left = column * spacing / spanCount
                outRect.right = spacing - (column + 1) * spacing / spanCount
                if (position >= spanCount) {
                    outRect.top = spacing
                }
            }
        }
    }
    
    data class BookStat(val name: String, val count: Int)
    
    class BookAdapter(private val books: List<BookStat>, private val onClick: (String) -> Unit) : RecyclerView.Adapter<BookAdapter.ViewHolder>() {
        private val colors = intArrayOf(0xFFC9A96E.toInt(), 0xFFE6937B.toInt(), 0xFF7BAED4.toInt(), 0xFF8BC48A.toInt(), 0xFFD4A0D4.toInt(), 0xFFF0C75E.toInt(), 0xFF7BC4C4.toInt(), 0xFFC47B7B.toInt(), 0xFF9B9BC4.toInt(), 0xFFC4B47B.toInt(), 0xFF7BC49B.toInt(), 0xFFC49B7B.toInt())
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val colorBar: View = view.findViewById(R.id.colorBar)
            val tvBookName: TextView = view.findViewById(R.id.tvBookName)
            val tvBookCount: TextView = view.findViewById(R.id.tvBookCount)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val book = books[position]; holder.colorBar.setBackgroundColor(colors[position % colors.size])
            holder.tvBookName.text = book.name; holder.tvBookCount.text = "${book.count} 题"; holder.itemView.setOnClickListener { onClick(book.name) }
        }
        override fun getItemCount() = books.size
    }
}
