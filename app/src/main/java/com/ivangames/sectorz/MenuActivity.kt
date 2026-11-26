package com.ivangames.sectorz

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText

class MenuActivity : Activity() {
    private lateinit var v: MenuView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = MenuView(this) { mode, skin ->
            if (mode == 3) askIp(skin) else start(mode, skin, "")
        }
        setContentView(v)
        hideBars()
    }

    private fun start(mode: Int, skin: Int, ip: String) {
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra("mode", mode).putExtra("skin", skin).putExtra("ip", ip)
        )
    }

    private fun askIp(skin: Int) {
        val prefs = getSharedPreferences("sectorz", MODE_PRIVATE)
        val et = EditText(this)
        et.setText(prefs.getString("ip", "192.168.") ?: "192.168.")
        val dlg = AlertDialog.Builder(this)
            .setTitle("Ищу хост в сети... (или введи IP)")
            .setView(et)
            .setPositiveButton("Подключиться") { _, _ ->
                val ip = et.text.toString().trim()
                prefs.edit().putString("ip", ip).apply()
                start(3, skin, ip)
            }
            .setNegativeButton("Отмена", null)
            .create()
        var th: Thread? = null
        dlg.setOnShowListener {
            th = Discovery.listen { ip ->
                runOnUiThread {
                    if (dlg.isShowing) {
                        et.setText(ip)
                        dlg.setTitle("Найден хост: " + ip)
                    }
                }
            }
        }
        dlg.setOnDismissListener { th?.interrupt() }
        dlg.show()
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
