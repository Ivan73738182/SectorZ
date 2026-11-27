package com.ivangames.sectorz

import android.graphics.*
import kotlin.math.*

object Medal {
    const val MAXL = 50

    fun need(l: Int): Int = 100 + 20 * (l - 1)

    fun total(l: Int): Int {
        var s = 0
        for (i in 1 until l) s += need(i)
        return s
    }

    val MAXXP: Int = total(MAXL)

    fun level(xp: Int): Int {
        var l = 1
        var r = xp
        while (l < MAXL && r >= need(l)) { r -= need(l); l++ }
        return l
    }

    fun into(xp: Int): Int = xp - total(level(xp))

    fun frac(xp: Int): Float {
        val l = level(xp)
        if (l >= MAXL) return 1f
        return into(xp).toFloat() / need(l)
    }

    private val rimC = intArrayOf(0xFF8D5524.toInt(), 0xFF78909C.toInt(), 0xFFF9A825.toInt(), 0xFF00838F.toInt(), 0xFFB71C1C.toInt())
    private val midC = intArrayOf(0xFFCD7F32.toInt(), 0xFFCFD8DC.toInt(), 0xFFFFD54F.toInt(), 0xFF4DD0E1.toInt(), 0xFFFF5252.toInt())
    private val ribC = intArrayOf(0xFFB71C1C.toInt(), 0xFF1565C0.toInt(), 0xFF2E7D32.toInt(), 0xFF6A1B9A.toInt(), 0xFFFFD600.toInt())
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pp = Path()

    private fun poly(cx: Float, cy: Float, r: Float, n: Int, inner: Float) {
        pp.reset()
        for (i in 0 until 2 * n) {
            val a = (i * PI / n - PI / 2.0).toFloat()
            val rr = if (i % 2 == 0) r else r * inner
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) pp.moveTo(x, y) else pp.lineTo(x, y)
        }
        pp.close()
    }

    fun draw(c: Canvas, cx: Float, cy: Float, r: Float, level: Int, locked: Boolean) {
        val tier = ((level - 1) / 10).coerceIn(0, 4)
        val k = (level - 1) % 10
        val rim = if (locked) 0xFF2A3036.toInt() else rimC[tier]
        val mid = if (locked) 0xFF444C54.toInt() else midC[tier]
        val rib = if (locked) 0xFF30363C.toInt() else ribC[tier]
        pt.style = Paint.Style.FILL
        if (tier >= 3 && !locked) {
            pt.color = (mid and 0x00FFFFFF) or 0x33000000
            c.drawCircle(cx, cy, r * 1.3f, pt)
        }
        pt.color = rib
        pp.reset()
        pp.moveTo(cx - r * 0.7f, cy + r * 0.3f)
        pp.lineTo(cx - r * 0.2f, cy + r * 0.6f)
        pp.lineTo(cx - r * 0.35f, cy + r * 1.5f)
        pp.lineTo(cx - r * 0.6f, cy + r * 1.2f)
        pp.lineTo(cx - r * 0.95f, cy + r * 1.4f)
        pp.close()
        c.drawPath(pp, pt)
        pp.reset()
        pp.moveTo(cx + r * 0.7f, cy + r * 0.3f)
        pp.lineTo(cx + r * 0.2f, cy + r * 0.6f)
        pp.lineTo(cx + r * 0.35f, cy + r * 1.5f)
        pp.lineTo(cx + r * 0.6f, cy + r * 1.2f)
        pp.lineTo(cx + r * 0.95f, cy + r * 1.4f)
        pp.close()
        c.drawPath(pp, pt)
        poly(cx, cy, r, 8 + k, 0.86f)
        pt.color = rim
        c.drawPath(pp, pt)
        pt.color = mid
        c.drawCircle(cx, cy, r * 0.76f, pt)
        pt.style = Paint.Style.STROKE
        pt.strokeWidth = r * 0.07f
        pt.color = rim
        c.drawCircle(cx, cy, r * 0.76f, pt)
        pt.style = Paint.Style.FILL
        pt.color = if (locked) 0x22FFFFFF else 0x44FFFFFF
        c.drawCircle(cx - r * 0.1f, cy - r * 0.12f, r * 0.52f, pt)
        if (level >= MAXL && !locked) {
            poly(cx, cy - r * 1.15f, r * 0.34f, 5, 0.45f)
            pt.color = 0xFFFFEB3B.toInt()
            c.drawPath(pp, pt)
        }
        pt.style = Paint.Style.FILL
        pt.color = if (locked) 0xFF6B747C.toInt() else 0xFF2B1B0E.toInt()
        pt.textAlign = Paint.Align.CENTER
        pt.typeface = Typeface.DEFAULT_BOLD
        pt.textSize = r * (if (level < 10) 0.95f else 0.78f)
        c.drawText(level.toString(), cx, cy + pt.textSize * 0.35f, pt)
    }
}
