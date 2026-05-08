package com.wangyu.gotsumego.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.wangyu.gotsumego.R

class WrongFragment : Fragment() {
    companion object { fun newInstance() = WrongFragment() }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_library, container, false)
        view.findViewById<TextView>(R.id.tvTotalCount).text = "错题本功能开发中..."
        return view
    }
}