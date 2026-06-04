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
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_library -> { showFragment(LibraryFragment.newInstance()); true }
                R.id.nav_random -> {
                    val intent = Intent(this, ProblemActivity::class.java)
                    intent.putExtra(ProblemActivity.EXTRA_RANDOM, true)
                    intent.putExtra(ProblemActivity.EXTRA_TITLE, "随机做题")
                    startActivity(intent)
                    false
                }
                else -> false
            }
        }
    }
    
    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()
    }
}
