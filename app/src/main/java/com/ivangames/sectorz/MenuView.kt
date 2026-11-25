package com.ivangames.sectorz

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class MenuView(ctx: Context, private val onPlay: (Int, Int) -> Unit) : View(ctx), Runnable {

    private class Walker(var x: Float, var y: Float, val vx: Float, val vy: Float, val kind: Int, val variant: Int) {
        var ph = 0f
    }

    private val d = resources.displayMetrics.density
    private val prefs = ctx.getSharedPreferences("sectorz", Context.MODE_PRIVATE)
    private var skin = prefs.getInt("skin", 0).coerceIn(0, 3)
    private var screen = 0
    private var pressed = -1
    private var time = 0f
    private var last = 0L
    private var running = false
    private val rnd = Random(5)
    private val walkers = ArrayList<Walker>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val btn = Array(3) { RectF() }
    private val cards = Array(4) { RectF() }
    private val backBtn = RectF()
    private var vig: Paint? = null
    private val labels = arrayOf("ИГРАТЬ", "МУЛЬТИПЛЕЕР", "НАСТРОЙКИ")
    private val subs = arrayOf("20 ботов-выживших, зомби и собаки", "до 4 игроков  •  сеть скоро", "")

    private fun initWalkers(w: Float, h: Float) {
        val rd = 100f * d
        val hy = floatArrayOf(h * 0.08f + rd / 2f, h * 0.80f + rd / 2f)
        val vxc = w * 0.5f + rd / 2f
        for (i in 0 until 9) {
            val kind = if (i % 4 == 3) 1 else if (i % 4 == 2) 2 else 0
            val mul = if (kind == 1) 3f else if (kind == 2) 2f else 1f
            val sp = (22f + rnd.nextFloat() * 30f) * d * mul
            val dir = if (rnd.nextBoolean()) 1f else -1f
            if (i < 6) {
                walkers.add(Walker(rnd.nextFloat() * w, hy[i % 2] + (rnd.nextFloat() - 0.5f) * 40f * d, sp * dir, 0f, kind, i))
            } else {
                walkers.add(Walker(vxc + (rnd.nextFloat() - 0.5f) * 40f * d, rnd.nextFloat() * h, 0f, sp * dir, kind, i))
            }
        }
    }

    private fun update(dt: Float) {
        time += dt
        val w = width.toFloat()
        val h = height.toFloat()
        val m = 100f * d
        for (k in walkers) {
            k.x += k.vx * dt
            k.y += k.vy * dt
            k.ph += dt * hypot(k.vx, k.vy) / d * 0.15f
            if (k.x > w + m) k.x = -m
            if (k.x < -m) k.x = w + m
            if (k.y > h + m) k.y = -m
            if (k.y < -m) k.y = h + m
        }
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, align: Paint.Align) {
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.textAlign = align
        p.color = 0xAA000000.toInt()
        c.drawText(s, x + 2f, y + 2f, p)
        p.color = col
        c.drawText(s, x, y, p)
    }

    private fun fr(c: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int) {
        p.style = Paint.Style.FILL
        p.color = col
        c.drawRect(l, t, r, b, p)
    }

    private fun drawButton(c: Canvas, r: RectF, label: String, sub: String, isPressed: Boolean) {
        rf.set(r)
        p.style = Paint.Style.FILL
        p.color = if (isPressed) 0xFFB71C1C.toInt() else 0xDD1B1F23.toInt()
        c.drawRoundRect(rf, 14f * d, 14f * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f * d
        p.color = 0xFFE53935.toInt()
        c.drawRoundRect(rf, 14f * d, 14f * d, p)
        if (sub.isEmpty()) {
            txt(c, label, r.centerX(), r.centerY() + 8f * d, 22f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        } else {
            txt(c, label, r.centerX(), r.centerY() - 2f * d, 22f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            txt(c, sub, r.centerX(), r.centerY() + 17f * d, 12f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        vig = null
        walkers.clear()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (walkers.isEmpty()) initWalkers(w, h)
        c.drawColor(0xFF10151A.toInt())
        val rd = 100f * d
        fr(c, 0f, h * 0.08f, w, h * 0.08f + rd, 0xFF1D2328.toInt())
        fr(c, 0f, h * 0.80f, w, h * 0.80f + rd, 0xFF1D2328.toInt())
        fr(c, w * 0.5f, 0f, w * 0.5f + rd, h, 0xFF1D2328.toInt())
        fr(c, w * 0.1f, h * 0.3f, w * 0.1f + 90f * d, h * 0.3f + 60f * d, 0xFF171C20.toInt())
        for (k in walkers) {
            c.save()
            c.translate(k.x, k.y)
            c.scale(2.3f * d, 2.3f * d)
            val ang = atan2(k.vy, k.vx)
            when (k.kind) {
                1 -> Sprites.dog(c, ang, k.ph, -1f, 0, true)
                2 -> Sprites.zombie(c, 1, k.variant, ang, k.ph, -1f, 0, true)
                else -> Sprites.zombie(c, 0, k.variant, ang, k.ph, -1f, 0, true)
            }
            c.restore()
        }
        val v = vig ?: Paint().apply {
            shader = RadialGradient(
                w / 2f, h / 2f, max(w, h) * 0.7f,
                intArrayOf(0x00000000, 0x66000000, 0xEE000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }.also { vig = it }
        c.drawRect(0f, 0f, w, h, v)

        if (screen == 0) drawMain(c, w, h) else drawSettings(c, w, h)
    }

    private fun drawMain(c: Canvas, w: Float, h: Float) {
        val cx = w * 0.27f
        val ts = min(h * 0.17f, 74f * d)
        p.letterSpacing = 0.08f
        txt(c, "SECTOR Z", cx, h * 0.22f, ts, 0xFFE53935.toInt(), Paint.Align.CENTER)
        p.letterSpacing = 0f
        txt(c, "Выживи в заброшенном городе", cx, h * 0.22f + 28f * d, 15f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)

        val bw = min(w * 0.38f, 420f * d)
        val bh = 68f * d
        val gap = 14f * d
        var y = h * 0.36f
        for (i in 0..2) {
            btn[i].set(cx - bw / 2f, y, cx + bw / 2f, y + bh)
            drawButton(c, btn[i], labels[i], subs[i], pressed == i)
            y += bh + gap
        }

        val cs = min(h * 0.34f, 150f * d)
        val g2 = 14f * d
        val total = cs * 2f + g2
        val cx2 = w * 0.74f
        val left = cx2 - total / 2f
        val top = h * 0.5f - total / 2f + 18f * d
        txt(c, "ВЫБЕРИ ГЕРОЯ", cx2, top - 16f * d, 18f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        for (i in 0..3) {
            val l = left + (i % 2) * (cs + g2)
            val t = top + (i / 2) * (cs + g2)
            cards[i].set(l, t, l + cs, t + cs)
            rf.set(cards[i])
            p.style = Paint.Style.FILL
            p.color = 0xAA1B2226.toInt()
            c.drawRoundRect(rf, 14f * d, 14f * d, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = (if (i == skin) 4f else 1.5f) * d
            p.color = if (i == skin) 0xFFFFA000.toInt() else 0x66FFFFFF
            c.drawRoundRect(rf, 14f * d, 14f * d, p)
            c.save()
            c.translate(rf.centerX(), rf.centerY() - cs * 0.06f)
            c.scale(cs / 62f, cs / 62f)
            Sprites.human(c, Looks.players[i], -0.5f, time * 6f, i == skin, -1f, 0f, 3, 0)
            c.restore()
            txt(c, Looks.names[i], rf.centerX(), rf.bottom - 10f * d, 14f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        }
    }

    private fun drawSettings(c: Canvas, w: Float, h: Float) {
        fr(c, 0f, 0f, w, h, 0xCC000000.toInt())
        txt(c, "НАСТРОЙКИ", w / 2f, h * 0.28f, 38f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
        txt(c, "Пока здесь пусто", w / 2f, h * 0.45f, 18f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)
        backBtn.set(w / 2f - 130f * d, h * 0.6f, w / 2f + 130f * d, h * 0.6f + 62f * d)
        drawButton(c, backBtn, "НАЗАД", "", pressed == 10)
    }

    private fun hit(x: Float, y: Float): Int {
        if (screen == 1) return if (backBtn.contains(x, y)) 10 else -1
        for (i in 0..2) if (btn[i].contains(x, y)) return i
        for (i in 0..3) if (cards[i].contains(x, y)) return 4 + i
        return -1
    }

    private fun save() {
        prefs.edit().putInt("skin", skin).apply()
    }

    private fun click(id: Int) {
        if (id == 0) { save(); onPlay(0, skin) }
        else if (id == 1) { save(); onPlay(1, skin) }
        else if (id == 2) screen = 1
        else if (id in 4..7) { skin = id - 4; save() }
        else if (id == 10) screen = 0
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressed = hit(e.x, e.y)
            MotionEvent.ACTION_UP -> {
                val hh = hit(e.x, e.y)
                if (hh != -1 && hh == pressed) click(hh)
                pressed = -1
            }
            MotionEvent.ACTION_CANCEL -> pressed = -1
        }
        return true
    }

    fun back(): Boolean {
        if (screen == 1) { screen = 0; return true }
        return false
    }

    override fun run() {
        if (!running) return
        val now = System.nanoTime()
        var dt = (now - last) / 1e9f
        last = now
        if (dt > 0.05f) dt = 0.05f
        update(dt)
        invalidate()
        postOnAnimation(this)
    }

    fun resume() {
        if (!running) {
            running = true
            last = System.nanoTime()
            postOnAnimation(this)
        }
    }

    fun pause() {
        running = false
        removeCallbacks(this)
    }
}
