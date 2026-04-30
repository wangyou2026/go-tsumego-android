package com.wangyu.gotsumego.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences("go_tsumego_settings", Context.MODE_PRIVATE)
        
        setupViews()
        loadSettings()
    }
    
    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }
        
        // 落子音效开关
        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }
        
        // 试下模式开关
        binding.switchTrialMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("trial_mode_enabled", isChecked).apply()
        }
    }
    
    private fun loadSettings() {
        val soundEnabled = prefs.getBoolean("sound_enabled", true)
        val trialModeEnabled = prefs.getBoolean("trial_mode_enabled", true)
        
        binding.switchSound.isChecked = soundEnabled
        binding.switchTrialMode.isChecked = trialModeEnabled
    }
}
