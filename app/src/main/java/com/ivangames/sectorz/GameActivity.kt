package com.ivangames.sectorz

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager

class GameActivity : Activity() {
    private lateinit var v: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val skin = intent.getIntExtra("skin", 0)
        val mode = intent.getIntExtra("mode", 0)
        val ip = intent.getStringExtra("ip") ?: ""
        Snd.init(applicationContext)
        v = GameView(this, skin, mode == 1, intent.getIntExtra("gm", 0)) { finish() }
        setContentView(v)
        v.startNet(mode, ip)
        if (mode == 4) v.openEditor()
        hideBars()
    }

    override fun onResume() { super.onResume(); v.resume(); hideBars(); Snd.music(true, 0.3f) }
    override fun onPause() { super.onPause(); v.pause(); Snd.music(false) }
    override fun onDestroy() { v.stopNet(); super.onDestroy() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars() }
    override fun onBackPressed() { if (!v.back()) finish() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean { // m16u
        if (v.key(keyCode, true, event.repeatCount == 0)) return true
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (v.key(keyCode, false, true)) return true
        return super.onKeyUp(keyCode, event)
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
}
