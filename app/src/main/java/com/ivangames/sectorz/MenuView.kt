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
    private var skin = prefs.getInt("skin", 0).coerceIn(0, 7)
    private var screen = 0
    private var pressed = -1
    private var time = 0f
    private var last = 0L
    private var running = false
    private val rnd = Random(5)
    private val walkers = ArrayList<Walker>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val btn = Array(4) { RectF() } // m15g
    private val cards = Array(4) { RectF() }
    private var skinPage = if (skin >= 4) 1 else 0 // m13s1
    private val pgBtn = RectF()
    private val backBtn = RectF()
    private val mp = Array(2) { RectF() }
    private val ctlBtn = RectF()
    private var pendKind = 0
    private val nameBtn = RectF()
    private val stBtn = Array(3) { RectF() }
    private val mm = Array(4) { RectF() } // m14u
    private var vig: Paint? = null
    private val labels = arrayOf("ИГРАТЬ", "МУЛЬТИПЛЕЕР", "НАСТРОЙКИ", "ОНЛАЙН") // m15o
    private val subs = arrayOf("режимы: свободный, волны, PVP", "по Wi-Fi: до 4 игроков", "", "через интернет, подбор 20 секунд") // m15p

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

        if (screen == 0) { drawMain(c, w, h); drawMedalChip(c, w, h) } else if (screen == 4) drawMedals(c, w, h) else if (screen == 1) drawSettings2(c, w, h) else if (screen == 3) drawModes(c, w, h) else if (screen == 5) drawSearch(c, w, h) else drawMulti(c, w, h)
    }

    private fun drawMain(c: Canvas, w: Float, h: Float) {
        val cx = w * 0.27f
        val ts = min(h * 0.17f, 74f * d)
        p.letterSpacing = 0.08f
        txt(c, "SECTOR Z", cx, h * 0.19f, ts, 0xFFE53935.toInt(), Paint.Align.CENTER)
        p.letterSpacing = 0f
        txt(c, "Выживи в заброшенном городе", cx, h * 0.19f + 28f * d, 15f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)

        val bw = min(w * 0.38f, 420f * d)
        val bh = min(60f * d, h * 0.125f) // m15h
        val gap = min(12f * d, h * 0.02f)
        var y = h * 0.33f
        for (i in 0..3) {
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
        nameBtn.set(cx2 - 115f * d, top - 46f * d, cx2 + 115f * d, top - 12f * d)
        drawButton(c, nameBtn, "ИМЯ: " + (prefs.getString("name", "Игрок") ?: "Игрок"), "", pressed == 30)
        for (i in 0..3) {
            val gi = skinPage * 4 + i
            val l = left + (i % 2) * (cs + g2)
            val t = top + (i / 2) * (cs + g2)
            cards[i].set(l, t, l + cs, t + cs)
            rf.set(cards[i])
            p.style = Paint.Style.FILL
            p.color = 0xAA1B2226.toInt()
            c.drawRoundRect(rf, 14f * d, 14f * d, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = (if (gi == skin) 4f else 1.5f) * d
            p.color = if (gi == skin) 0xFFFFA000.toInt() else 0x66FFFFFF
            c.drawRoundRect(rf, 14f * d, 14f * d, p)
            c.save()
            c.translate(rf.centerX(), rf.centerY() - cs * 0.06f)
            c.scale(cs / 62f, cs / 62f)
            Sprites.human(c, Looks.players[gi], -0.5f, time * 6f, gi == skin, -1f, 0f, 3, 0)
            c.restore()
            txt(c, Looks.names[gi], rf.centerX(), rf.bottom - 10f * d, 14f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        }
        pgBtn.set(left + total + 4f * d, top + total / 2f - 26f * d, left + total + 60f * d, top + total / 2f + 26f * d) // m13s2
        rf.set(pgBtn)
        p.style = Paint.Style.FILL
        p.color = 0xAA1B2226.toInt()
        c.drawRoundRect(rf, 12f * d, 12f * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * d
        p.color = 0x99FFA000.toInt()
        c.drawRoundRect(rf, 12f * d, 12f * d, p)
        txt(c, "СКИНЫ", pgBtn.centerX(), pgBtn.centerY() - 2f * d, 11f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        txt(c, (skinPage + 1).toString() + "/2", pgBtn.centerX(), pgBtn.centerY() + 14f * d, 12f * d, 0xFFFFCA28.toInt(), Paint.Align.CENTER)
    }

    private fun drawSettings(c: Canvas, w: Float, h: Float) {
        fr(c, 0f, 0f, w, h, 0xCC000000.toInt())
        txt(c, "НАСТРОЙКИ", w / 2f, h * 0.28f, 38f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
        ctlBtn.set(w / 2f - 190f * d, h * 0.40f, w / 2f + 190f * d, h * 0.40f + 62f * d)
        drawButton(c, ctlBtn, "КАСТОМИЗАЦИЯ УПРАВЛЕНИЯ", "размер и положение кнопок", pressed == 13)
        backBtn.set(w / 2f - 130f * d, h * 0.6f, w / 2f + 130f * d, h * 0.6f + 62f * d)
        drawButton(c, backBtn, "НАЗАД", "", pressed == 10)
    }

    private fun hit(x: Float, y: Float): Int {
        if (screen == 4 || screen == 5) return if (backBtn.contains(x, y)) 10 else -1 // m15j
        if (screen == 3) {
            for (i in 0..(if (pendKind == 2) 3 else 2)) if (mm[i].contains(x, y)) return 20 + i // m14v
            return if (backBtn.contains(x, y)) 10 else -1
        }
        if (screen == 2) {
            if (mp[0].contains(x, y)) return 11
            if (mp[1].contains(x, y)) return 12
            return if (backBtn.contains(x, y)) 10 else -1
        }
        if (screen == 1) {
            if (ctlBtn.contains(x, y)) return 13
            for (i in 0..2) if (stBtn[i].contains(x, y)) return 40 + i
            return if (backBtn.contains(x, y)) 10 else -1
        }
        for (i in 0..3) if (btn[i].contains(x, y)) return i // m15i
        if (nameBtn.contains(x, y)) return 30
        if (medalR.contains(x, y)) return 31
        if (pgBtn.contains(x, y)) return 50
        for (i in 0..3) if (cards[i].contains(x, y)) return 4 + i
        return -1
    }

    private fun save() {
        prefs.edit().putInt("skin", skin).apply()
    }

    private fun click(id: Int) {
        Snd.play(Snd.CLICK, 1f)
        if (id == 0) { pendKind = 0; screen = 3 }
        else if (id == 3) { save(); screen = 5; Online.find() } // m15k
        else if (id in 20..23) { save(); onPlay(pendKind + 10 * (id - 20), skin) }
        else if (id == 1) screen = 2
        else if (id == 11) { pendKind = 2; screen = 3 }
        else if (id == 12) { save(); onPlay(3, skin) }
        else if (id == 13) { save(); onPlay(4, skin) }
        else if (id == 30) onPlay(99, 0)
        else if (id == 31) screen = 4
        else if (id == 40) Cfg.nextVol(context)
        else if (id == 41) Cfg.nextAim(context)
        else if (id == 42) Cfg.nextFps(context)
        else if (id == 2) screen = 1
        else if (id == 50) skinPage = 1 - skinPage
        else if (id in 4..7) { skin = skinPage * 4 + (id - 4); save() }
        else if (id == 10) { screen = 0; if (!Online.inGame) Online.cancel() } // m15l
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
        if (screen != 0) { if (screen == 5 && !Online.inGame) Online.cancel(); screen = 0; return true } // m15m
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

    private fun drawMulti(c: Canvas, w: Float, h: Float) {
        fr(c, 0f, 0f, w, h, 0xCC000000.toInt())
        txt(c, "МУЛЬТИПЛЕЕР", w / 2f, h * 0.2f, 34f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
        txt(c, "Все игроки в одной Wi-Fi сети (или на раздаче телефона)", w / 2f, h * 0.2f + 26f * d, 13f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)
        val bw = min(w * 0.5f, 420f * d)
        val bh = min(64f * d, h * 0.14f)
        val g = min(14f * d, h * 0.025f)
        var y = h * 0.38f
        mp[0].set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh)
        drawButton(c, mp[0], "СОЗДАТЬ ИГРУ", "ты хост, остальные подключаются к тебе", pressed == 11)
        y += bh + g
        mp[1].set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh)
        drawButton(c, mp[1], "ПОДКЛЮЧИТЬСЯ", "ввести IP хоста", pressed == 12)
        y += bh + g
        backBtn.set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh)
        drawButton(c, backBtn, "НАЗАД", "", pressed == 10)
    }


    private fun drawSearch(c: Canvas, w: Float, h: Float) {
        fr(c, 0f, 0f, w, h, 0xCC000000.toInt())
        txt(c, "ОНЛАЙН", w / 2f, h * 0.2f, 34f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
        val dots = ".".repeat(((time * 2f).toInt()) % 4)
        txt(c, "Подбор игроков" + dots, w / 2f, h * 0.32f, 20f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        txt(c, Online.count.toString() + " / 4", w / 2f, h * 0.5f, 56f * d, 0xFFFFCA28.toInt(), Paint.Align.CENTER)
        txt(c, "До старта: " + Online.left + " с   (минимум 2 игрока)", w / 2f, h * 0.58f, 14f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)
        txt(c, Online.status, w / 2f, h * 0.65f, 13f * d, 0xFF80D8FF.toInt(), Paint.Align.CENTER)
        val bw = min(w * 0.4f, 320f * d)
        val bh = min(56f * d, h * 0.12f)
        backBtn.set(w / 2f - bw / 2f, h * 0.74f, w / 2f + bw / 2f, h * 0.74f + bh)
        drawButton(c, backBtn, "ОТМЕНА", "", pressed == 10)
        if (Online.started) {
            Online.started = false
            screen = 0
            onPlay(98, skin)
        }
    }

    private fun drawModes(c: Canvas, w: Float, h: Float) {
        fr(c, 0f, 0f, w, h, 0xCC000000.toInt())
        txt(c, "ВЫБЕРИ РЕЖИМ", w / 2f, h * 0.17f, 34f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
        val bw = min(w * 0.55f, 460f * d)
        val bh = min(66f * d, h * 0.115f) // m14w
        val g = min(12f * d, h * 0.018f)
        var y = h * 0.23f
        val tt = arrayOf("СВОБОДНЫЙ", "ВОЛНЫ", "PVP", "ЗАХВАТ БАЗЫ")
        val ss = arrayOf("город, боты-выжившие, зомби и собаки", "зомби идут волнами, союзников нет", "зона сужается, каждый сам за себя", "команды 2 на 2, держи точку в центре")
        for (i in 0..(if (pendKind == 2) 3 else 2)) {
            mm[i].set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh)
            drawButton(c, mm[i], tt[i], ss[i], pressed == 20 + i)
            y += bh + g
        }
        backBtn.set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh * 0.8f)
        drawButton(c, backBtn, "НАЗАД", "", pressed == 10)
    }



    private fun drawSettings2(c: Canvas, w: Float, h: Float) {
        fr(c, 0f, 0f, w, h, 0xCC000000.toInt())
        txt(c, "НАСТРОЙКИ", w / 2f, h * 0.15f, 34f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
        val bw = min(w * 0.55f, 420f * d)
        val bh = min(52f * d, h * 0.11f)
        val g = min(9f * d, h * 0.02f)
        var y = h * 0.22f
        ctlBtn.set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh)
        drawButton(c, ctlBtn, "КАСТОМИЗАЦИЯ УПРАВЛЕНИЯ", "", pressed == 13)
        y += bh + g
        val lab = arrayOf(Cfg.volLabel(), Cfg.aimLabel(), Cfg.fpsLabel())
        for (i in 0..2) {
            stBtn[i].set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh)
            drawButton(c, stBtn[i], lab[i], "", pressed == 40 + i)
            y += bh + g
        }
        backBtn.set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh)
        drawButton(c, backBtn, "НАЗАД", "", pressed == 10)
    }


    private val medalR = RectF()

    private fun drawMedalChip(c: Canvas, w: Float, h: Float) {
        val xp = prefs.getInt("medalXp", 0)
        val lv = Medal.level(xp)
        val cx = w - 40f * d
        val cy = 40f * d
        Medal.draw(c, cx, cy, 24f * d, lv, pressed == 31)
        medalR.set(cx - 34f * d, cy - 30f * d, cx + 34f * d, cy + 84f * d)
        txt(c, "Ранг " + lv, cx, cy + 56f * d, 12f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        rf.set(cx - 30f * d, cy + 62f * d, cx + 30f * d, cy + 68f * d)
        c.drawRoundRect(rf, 3f * d, 3f * d, fillP2(0x66FFFFFF.toInt()))
        rf.right = rf.left + 60f * d * Medal.frac(xp)
        c.drawRoundRect(rf, 3f * d, 3f * d, fillP2(0xFFFFC107.toInt()))
        if (lv < Medal.MAXL) txt(c, Medal.into(xp).toString() + "/" + Medal.need(lv), cx, cy + 82f * d, 10f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)
    }

    private fun fillP2(col: Int): Paint {
        p.style = Paint.Style.FILL
        p.color = col
        return p
    }

    private fun drawMedals(c: Canvas, w: Float, h: Float) {
        fr(c, 0f, 0f, w, h, 0xDD000000.toInt())
        val xp = prefs.getInt("medalXp", 0)
        val lv = Medal.level(xp)
        txt(c, "МЕДАЛИ   ранг " + lv + " из " + Medal.MAXL, w / 2f, h * 0.1f, 28f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
        val sub = if (lv < Medal.MAXL) "Опыт: " + Medal.into(xp) + " / " + Medal.need(lv) + " до следующего ранга" else "Максимальный ранг!"
        txt(c, sub, w / 2f, h * 0.1f + 24f * d, 13f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)
        val cw = w * 0.94f / 10f
        val ch = h * 0.58f / 5f
        val r = min(cw * 0.40f, ch / 2.7f)
        for (i in 0 until 50) {
            val col = i % 10
            val row = i / 10
            Medal.draw(c, w * 0.03f + cw * (col + 0.5f), h * 0.22f + ch * row + r * 1.05f, r, i + 1, i + 1 > lv)
        }
        backBtn.set(w / 2f - 130f * d, h * 0.88f, w / 2f + 130f * d, h * 0.88f + min(50f * d, h * 0.09f))
        drawButton(c, backBtn, "НАЗАД", "", pressed == 10)
    }

}
