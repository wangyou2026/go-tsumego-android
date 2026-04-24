package com.wangyu.gotsumego

import android.app.Application
import com.wangyu.gotsumego.data.ProblemRepository

class TsumegoApp : Application() {
    
    lateinit var repository: ProblemRepository
        private set
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = ProblemRepository(this)
    }
    
    companion object {
        lateinit var instance: TsumegoApp
            private set
    }
}
