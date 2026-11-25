package com.ivangames.sectorz

import android.graphics.*
import kotlin.math.*

object Sprites {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val whiteF = PorterDuffColorFilter(0xCCFFFFFF.toInt(), PorterDuff.Mode.SRC_ATOP)
    private val darkF = PorterDuffColorFilter(0x99000000.toInt(), PorterDuff.Mode.SRC_ATOP)
    private val zShirts = intArrayOf(0xFF5B4B3A.toInt(), 0xFF3C4A5C.toInt(), 0xFF6B3A3A.toInt(), 0xFF4A5B3A.toInt(), 0xFF555555.toInt(), 0xFF6B5B2E.toInt())

    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, col: Int) {
        rf.set(cx - rx, cy - ry, cx + rx, cy + ry)
        p.style = Paint.Style.FILL
        p.color = col
        c.drawOval(rf, p)
    }

    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int) {
        p.style = Paint.Style.FILL
        p.color = col
        c.drawRect(l, t, r, b, p)
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        p.color = col
        c.drawLine(x1, y1, x2, y2, p)
        p.style = Paint.Style.FILL
    }

    private fun filter(mode: Int): PorterDuffColorFilter? = when (mode) {
        1 -> whiteF
        2 -> darkF
        else -> null
    }

    // mode: 0 обычный, 1 получил урон, 2 мёртв
    fun human(c: Canvas, look: Look, ang: Float, walk: Float, moving: Boolean, atkT: Float, flash: Float, weapon: Int, mode: Int) {
        p.colorFilter = null
        c.save()
        c.rotate(Math.toDegrees(ang.toDouble()).toFloat())
        if (mode == 2) oval(c, 0f, 0f, 17f, 13f, 0x88661010.toInt())
        oval(c, 0f, 1f, 12f, 15f, 0x44000000)
        p.colorFilter = filter(mode)
        val sw = if (moving && mode != 2) sin(walk) * 7f else 0f
        oval(c, sw, -6f, 6.5f, 4f, look.pants)
        oval(c, -sw, 6f, 6.5f, 4f, look.pants)
        oval(c, 0f, 0f, 8f, 13f, look.shirt)
        if (mode == 2) {
            line(c, 2f, -10f, 12f, -16f, 4f, look.shirt)
            line(c, 2f, 10f, 12f, 16f, 4f, look.shirt)
            oval(c, 13f, -17f, 3f, 3f, look.skin)
            oval(c, 13f, 17f, 3f, 3f, look.skin)
        } else if (weapon >= 3) {
            val len = W.gunLen[weapon]
            val rec = if (flash > 0f) -2.5f else 0f
            line(c, 2f, 9f, 11f + rec, 4f, 4.2f, look.shirt)
            line(c, 2f, -9f, 9f + len * 0.55f + rec, 1.5f, 4.2f, look.shirt)
            line(c, 8f + rec, 3f, 10f + len + rec, 3f, 4.6f, 0xFF262626.toInt())
            if (weapon == 4) box(c, 12f + rec, 3f, 15f + rec, 10f, 0xFF3A3A3A.toInt())
            if (weapon == 5) line(c, 8f + rec, 3f, 8f + len * 0.4f + rec, 3f, 4.8f, 0xFF6D4C2F.toInt())
            oval(c, 11f + rec, 4f, 3.2f, 3.2f, look.skin)
            oval(c, 9f + len * 0.55f + rec, 1.5f, 3.2f, 3.2f, look.skin)
            if (flash > 0f) {
                oval(c, 14f + len + rec, 3f, 6.5f, 6.5f, 0xFFFFD54F.toInt())
                oval(c, 14f + len + rec, 3f, 3f, 3f, 0xFFFFFFFF.toInt())
            }
        } else {
            val a = if (atkT >= 0f) lerpF(1.15f, -0.95f, atkT.coerceIn(0f, 1f)) else -0.2f
            val hx = 2f + cos(a) * 14f
            val hy = 9f + sin(a) * 12f
            line(c, 1f, 9f, hx, hy, 4.2f, look.shirt)
            if (weapon == 1) line(c, hx, hy, hx + cos(a) * 14f, hy + sin(a) * 14f, 2.6f, 0xFFCFD8DC.toInt())
            if (weapon == 2) line(c, hx, hy, hx + cos(a) * 26f, hy + sin(a) * 26f, 5f, 0xFF8D6E3F.toInt())
            oval(c, hx, hy, 3.4f, 3.4f, look.skin)
            val lx = 11f + (if (moving) sw * 0.3f else 0f)
            line(c, 1f, -9f, lx, -7f, 4.2f, look.shirt)
            oval(c, lx, -7f, 3.2f, 3.2f, look.skin)
        }
        oval(c, 1.5f, 0f, 6.5f, 6.5f, look.skin)
        oval(c, -1f, 0f, 6.8f, 7f, look.hair)
        when (look.hat) {
            1 -> {
                oval(c, 0f, 0f, 7f, 7f, look.hatColor)
                box(c, 5f, -4.5f, 11f, 4.5f, look.hatColor)
            }
            2 -> oval(c, -1.5f, 0f, 9f, 9f, look.hatColor)
            3 -> {
                oval(c, 0f, 0f, 8f, 8f, 0xFF4C5B3A.toInt())
                oval(c, 1.5f, -2.5f, 3f, 2f, 0x66FFFFFF)
            }
            4 -> box(c, 3f, -7f, 6f, 7f, look.hatColor)
        }
        c.restore()
        p.colorFilter = null
    }

    // type: 0 обычный, 1 бегун, 2 здоровяк
    fun zombie(c: Canvas, type: Int, variant: Int, ang: Float, walk: Float, atkT: Float, mode: Int, moving: Boolean) {
        p.colorFilter = null
        val sc = when (type) {
            1 -> 0.92f
            2 -> 1.45f
            else -> 1f
        }
        val skin = when (type) {
            1 -> 0xFFA9B77E.toInt()
            2 -> 0xFF6B8765.toInt()
            else -> 0xFF86A86B.toInt()
        }
        val shirt = if (type == 2) 0xFF3B2F2F.toInt() else zShirts[variant % zShirts.size]
        c.save()
        c.rotate(Math.toDegrees(ang.toDouble()).toFloat())
        c.scale(sc, sc)
        if (mode == 2) oval(c, 0f, 0f, 17f, 13f, 0x88661010.toInt())
        oval(c, 0f, 1f, 12f, 15f, 0x44000000)
        p.colorFilter = filter(mode)
        val sw = if (moving && mode != 2) sin(walk) * 5f else 0f
        oval(c, sw, -6f, 6.5f, 4f, 0xFF343A40.toInt())
        oval(c, -sw, 6f, 6.5f, 4f, 0xFF343A40.toInt())
        oval(c, 0f, 0f, 8f, 13f, shirt)
        oval(c, -2f, 5f, 3f, 2f, skin)
        oval(c, 1f, -4f, 2.5f, 2f, 0xFF7A1515.toInt())
        if (mode == 2) {
            line(c, 2f, -10f, 10f, -15f, 4f, skin)
            line(c, 2f, 10f, 10f, 15f, 4f, skin)
        } else {
            val ext = 15f + (if (atkT >= 0f) sin(atkT.coerceIn(0f, 1f) * PI_F) * 9f else 0f)
            val sway = sin(walk * 0.7f) * 2.5f
            line(c, 2f, -9f, ext, -6f + sway, 4f, skin)
            line(c, 2f, 9f, ext, 6f - sway, 4f, skin)
            oval(c, ext, -6f + sway, 3.3f, 3.3f, 0xFF5E7A52.toInt())
            oval(c, ext, 6f - sway, 3.3f, 3.3f, 0xFF5E7A52.toInt())
        }
        oval(c, 2f, 0f, 7f, 7f, skin)
        oval(c, -1f, -1f, 5.5f, 5.5f, 0xFF2A2A22.toInt())
        if (mode != 2) {
            oval(c, 6.5f, -2.5f, 1.3f, 1.3f, 0xFFFF3030.toInt())
            oval(c, 6.5f, 2.5f, 1.3f, 1.3f, 0xFFFF3030.toInt())
        }
        oval(c, 7.8f, 0f, 1.6f, 2.2f, 0xFF7A1515.toInt())
        c.restore()
        p.colorFilter = null
    }

    fun dog(c: Canvas, ang: Float, walk: Float, atkT: Float, mode: Int, moving: Boolean) {
        p.colorFilter = null
        c.save()
        c.rotate(Math.toDegrees(ang.toDouble()).toFloat())
        if (mode == 2) oval(c, 0f, 0f, 17f, 11f, 0x88661010.toInt())
        oval(c, 0f, 1f, 15f, 9f, 0x44000000)
        p.colorFilter = filter(mode)
        val fur = 0xFF6B6B5A.toInt()
        val fur2 = 0xFF8A7A5C.toInt()
        val dark = 0xFF45453A.toInt()
        val s = if (moving && mode != 2) sin(walk) * 5f else 0f
        oval(c, 8f + s, -5f, 4.5f, 2.5f, dark)
        oval(c, 8f - s, 5f, 4.5f, 2.5f, dark)
        oval(c, -8f - s, -5f, 4.5f, 2.5f, dark)
        oval(c, -8f + s, 5f, 4.5f, 2.5f, dark)
        val tail = if (moving) sin(walk * 0.7f) * 5f else 0f
        line(c, -12f, 0f, -20f, tail, 3f, fur)
        oval(c, 0f, 0f, 13f, 6.5f, fur)
        oval(c, -3f, 2f, 5f, 3f, fur2)
        val hx = 14f + (if (atkT >= 0f) sin(atkT.coerceIn(0f, 1f) * PI_F) * 5f else 0f)
        oval(c, hx, 0f, 5.5f, 5f, fur)
        oval(c, hx + 5f, 0f, 4f, 2.8f, fur2)
        oval(c, hx + 8.5f, 0f, 1.4f, 1.4f, 0xFF111111.toInt())
        oval(c, hx - 2.5f, -4.5f, 2.5f, 1.8f, dark)
        oval(c, hx - 2.5f, 4.5f, 2.5f, 1.8f, dark)
        if (mode != 2) {
            oval(c, hx + 1.5f, -2.3f, 1.1f, 1.1f, 0xFFFF3030.toInt())
            oval(c, hx + 1.5f, 2.3f, 1.1f, 1.1f, 0xFFFF3030.toInt())
        }
        if (atkT >= 0f) oval(c, hx + 7f, 0f, 2.5f, 2f, 0xFFAA1111.toInt())
        c.restore()
        p.colorFilter = null
    }

    private fun weaponIcon(c: Canvas, id: Int) {
        val dk = 0xFF262626.toInt()
        when (id) {
            1 -> {
                line(c, -4f, 0f, 8f, 0f, 3f, 0xFFCFD8DC.toInt())
                line(c, -9f, 0f, -4f, 0f, 4f, 0xFF4E342E.toInt())
            }
            2 -> line(c, -10f, 0f, 10f, 0f, 4.5f, 0xFF8D6E3F.toInt())
            3 -> {
                line(c, -5f, 0f, 6f, 0f, 4f, dk)
                box(c, -5f, 0f, -1f, 6f, dk)
            }
            4 -> {
                line(c, -9f, 0f, 9f, 0f, 4.5f, dk)
                box(c, -1f, 0f, 3f, 8f, 0xFF3A3A3A.toInt())
            }
            else -> {
                line(c, -12f, 0f, 12f, 0f, 4f, dk)
                line(c, -12f, 0f, -4f, 0f, 5f, 0xFF6D4C2F.toInt())
                box(c, 0f, 0f, 3f, 6f, 0xFF3A3A3A.toInt())
            }
        }
    }

    // type: 0 патроны, 1 аптечка, 2 оружие
    fun loot(c: Canvas, type: Int, id: Int, t: Float) {
        p.colorFilter = null
        val bob = sin(t * 3f) * 2f
        val glow = when (type) {
            0 -> 0x44FFD54F
            1 -> 0x4466FF99
            else -> 0x4466CCFF
        }
        oval(c, 0f, 0f, 16f + sin(t * 4f) * 1.5f, 16f + sin(t * 4f) * 1.5f, glow)
        c.save()
        c.translate(0f, bob)
        when (type) {
            0 -> {
                box(c, -8f, -6f, 8f, 6f, 0xFF8B6B2E.toInt())
                box(c, -6f, -9f, -3f, -4f, 0xFFFFC107.toInt())
                box(c, -1.5f, -9f, 1.5f, -4f, 0xFFFFC107.toInt())
                box(c, 3f, -9f, 6f, -4f, 0xFFFFC107.toInt())
            }
            1 -> {
                box(c, -8f, -6f, 8f, 6f, 0xFFF5F5F5.toInt())
                box(c, -1.5f, -5f, 1.5f, 5f, 0xFFD32F2F.toInt())
                box(c, -5f, -1.5f, 5f, 1.5f, 0xFFD32F2F.toInt())
            }
            else -> {
                c.rotate(-30f)
                weaponIcon(c, id)
            }
        }
        c.restore()
    }
}
