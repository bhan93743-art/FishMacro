package com.fishmacro

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "낚시 매크로 빌드 테스트 성공"
        tv.textSize = 24f
        tv.setPadding(48, 200, 48, 48)
        setContentView(tv)
    }
}
