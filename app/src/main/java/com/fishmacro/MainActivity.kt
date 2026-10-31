package com.fishmacro

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 200, 48, 48)
        val tv = TextView(this)
        tv.text = "1. 아래 버튼으로 접근성 설정을 열어요\n2. 낚시매크로를 켜요\n3. 로블록스에서 낚시터로 가요\n4. 화면 위 시작 버튼을 눌러요"
        tv.textSize = 18f
        val btn = Button(this)
        btn.text = "접근성 설정 열기"
        btn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(tv)
        root.addView(btn)
        setContentView(root)
    }
}
