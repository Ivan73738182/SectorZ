package com.ivangames.sectorz

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View

class MenuActivity : Activity() {
    private lateinit var v: MenuView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = MenuView(this) { mode, skin ->
            startActivity(Intent(this, GameActivity::class.java).putExtra("mode", mode).putExtra("skin", skin))
        }
        setContentView(v)
        hideBars()
    }

    override fun onResume() { super.onResume(); v.resume(); hideBars() }
    override fun onPause() { super.onPause(); v.pause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars() }
    override fun onBackPressed() { if (!v.back()) super.onBackPressed() }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
}
