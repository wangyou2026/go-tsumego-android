package com.wangyu.gotsumego.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.TsumegoApp
import com.wangyu.gotsumego.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val repository by lazy { TsumegoApp.instance.repository }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTotalProblems.text = "${repository.getAllProblems().size} 道题目"
        setupBottomNav()
        if (savedInstanceState == null) showFragment(LibraryFragment.newInstance())
    }
    
    private fun setupBottomNav() {
        binding.navLibrary.setOnClickListener {
            highlightNav(binding.navLibrary)
            showFragment(LibraryFragment.newInstance())
        }
        binding.navRandom.setOnClickListener {
            highlightNav(binding.navRandom)
            val intent = Intent(this, ProblemActivity::class.java)
            intent.putExtra(ProblemActivity.EXTRA_RANDOM, true)
            intent.putExtra(ProblemActivity.EXTRA_TITLE, "随机做题")
            startActivity(intent)
        }
        binding.navManage.setOnClickListener {
            highlightNav(binding.navManage)
            val intent = Intent(this, LibraryManagementActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun highlightNav(selected: android.widget.TextView) {
        val normalColor = android.graphics.Color.parseColor("#E8D5A8")
        val activeColor = android.graphics.Color.parseColor("#FFFFFF")
        binding.navLibrary.setTextColor(if (selected == binding.navLibrary) activeColor else normalColor)
        binding.navLibrary.setTypeface(null, if (selected == binding.navLibrary) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.navRandom.setTextColor(if (selected == binding.navRandom) activeColor else normalColor)
        binding.navRandom.setTypeface(null, if (selected == binding.navRandom) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.navManage.setTextColor(if (selected == binding.navManage) activeColor else normalColor)
        binding.navManage.setTypeface(null, if (selected == binding.navManage) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
    
    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()
    }
}
