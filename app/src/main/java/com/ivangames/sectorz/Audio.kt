package com.ivangames.sectorz

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random

object Cfg {
    var vol = 3
    var aimI = 2
    var fps = false
    val vols = floatArrayOf(0f, 0.33f, 0.66f, 1f)
    val aims = floatArrayOf(0.6f, 0.8f, 1f, 1.3f, 1.7f)
    val aim: Float get() = aims[aimI]

    fun load(c: Context) {
        val p = c.getSharedPreferences("sectorz", Context.MODE_PRIVATE)
        vol = p.getInt("vol", 3).coerceIn(0, 3)
        aimI = p.getInt("aim", 2).coerceIn(0, 4)
        fps = p.getBoolean("fps", false)
    }

    private fun save(c: Context) {
        c.getSharedPreferences("sectorz", Context.MODE_PRIVATE).edit()
            .putInt("vol", vol).putInt("aim", aimI).putBoolean("fps", fps).apply()
    }

    fun nextVol(c: Context) { vol = (vol + 1) % 4; save(c); Snd.refresh() }
    fun nextAim(c: Context) { aimI = (aimI + 1) % 5; save(c) }
    fun nextFps(c: Context) { fps = !fps; save(c) }
    fun volLabel(): String = "ЗВУК: " + (vols[vol] * 100f).roundToInt() + "%"
    fun aimLabel(): String = "ЧУВСТВ. ПРИЦЕЛА: " + (aims[aimI] * 100f).roundToInt() + "%"
    fun fpsLabel(): String = "ПОКАЗ FPS: " + (if (fps) "ВКЛ" else "ВЫКЛ")
}

object Snd {
    const val SHOT_PISTOL = 0
    const val SHOT_AUTO = 1
    const val SHOT_RIFLE = 2
    const val SWING = 3
    const val HIT = 4
    const val GROAN = 5
    const val BARK = 6
    const val HURT = 7
    const val PICKUP = 8
    const val BOOM = 9
    const val CLICK = 10
    const val DEATH = 11
    const val MUSIC = 12
    private const val SR = 22050

    private val rnd = Random(3)
    private var pool: SoundPool? = null
    private val ids = IntArray(13)
    private var inited = false
    private var wantMusic = false
    private var mvol = 0.5f
    private var musicStream = 0

    fun shotFor(w: Int): Int = if (w == 3) SHOT_PISTOL else if (w == 4) SHOT_AUTO else SHOT_RIFLE

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        Cfg.load(ctx)
        val sp = SoundPool.Builder()
            .setMaxStreams(14)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
        pool = sp
        sp.setOnLoadCompleteListener { _, sid, st ->
            if (st == 0 && sid == ids[MUSIC] && wantMusic) music(true, mvol)
        }
        val dir = ctx.cacheDir
        val t = Thread {
            for (k in 0..12) {
                try {
                    val f = File(dir, "snd" + k + ".wav")
                    writeWav(f, gen(k))
                    ids[k] = sp.load(f.absolutePath, 1)
                } catch (e: Exception) { }
            }
        }
        t.isDaemon = true
        t.start()
    }

    fun play(id: Int, v: Float, rate: Float = 1f) {
        val sp = pool ?: return
        val vv = v * Cfg.vols[Cfg.vol]
        if (vv <= 0.01f || ids[id] == 0) return
        sp.play(ids[id], vv.coerceAtMost(1f), vv.coerceAtMost(1f), 1, 0, rate)
    }

    fun music(on: Boolean, v: Float = 0.5f) {
        wantMusic = on
        mvol = v
        val sp = pool ?: return
        if (musicStream != 0) { sp.stop(musicStream); musicStream = 0 }
        if (on && ids[MUSIC] != 0) {
            val vv = v * Cfg.vols[Cfg.vol]
            if (vv > 0.01f) musicStream = sp.play(ids[MUSIC], vv, vv, 0, -1, 1f)
        }
    }

    fun refresh() { music(wantMusic, mvol) }

    private fun nz(): Float = rnd.nextFloat() * 2f - 1f

    private fun mk(sec: Float, fn: (Float) -> Float): ShortArray {
        val n = (SR * sec).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i / SR.toFloat()
            var v = fn(t) * 0.9f
            if (i < 40) v *= i / 40f
            if (i > n - 40) v *= (n - i) / 40f
            out[i] = (v.coerceIn(-1f, 1f) * 32000f).toInt().toShort()
        }
        return out
    }

    private fun gen(k: Int): ShortArray {
        val tp = 2f * PI_F
        return when (k) {
            SHOT_PISTOL -> mk(0.22f) { t -> nz() * exp(-t * 38f) * 0.9f + sin(tp * 120f * t) * exp(-t * 28f) * 0.8f + sin(tp * 60f * t) * exp(-t * 18f) * 0.4f }
            SHOT_AUTO -> mk(0.12f) { t -> nz() * exp(-t * 70f) * 0.9f + sin(tp * 180f * t) * exp(-t * 50f) * 0.6f }
            SHOT_RIFLE -> mk(0.35f) { t -> nz() * exp(-t * 25f) * 0.9f + sin(tp * 80f * t) * exp(-t * 14f) + sin(tp * 45f * t) * exp(-t * 9f) * 0.6f }
            SWING -> {
                var y = 0f
                mk(0.18f) { t -> y += 0.25f * (nz() - y); y * sin(PI_F * t / 0.18f) * 2.5f }
            }
            HIT -> mk(0.14f) { t -> sin(tp * 75f * t) * exp(-t * 30f) + nz() * exp(-t * 60f) * 0.5f }
            GROAN -> mk(0.6f) { t ->
                val ph = tp * 85f * t + 3f * (1f - cos(tp * 5f * t))
                val env = sin(PI_F * t / 0.6f)
                (sin(ph) + 0.5f * sin(2f * ph) + 0.33f * sin(3f * ph) + 0.25f * sin(4f * ph)) * 0.5f * env + nz() * 0.1f * env
            }
            BARK -> mk(0.32f) { t ->
                val u = if (t < 0.16f) t else t - 0.16f
                (sin(tp * (520f * u - 600f * u * u)) * 0.7f + nz() * 0.4f) * exp(-u * 16f)
            }
            HURT -> mk(0.25f) { t -> sin(tp * (300f * t - 300f * t * t)) * exp(-t * 9f) * 0.8f + nz() * exp(-t * 30f) * 0.3f }
            PICKUP -> mk(0.16f) { t ->
                val u = if (t < 0.08f) t else t - 0.08f
                sin(tp * (if (t < 0.08f) 880f else 1320f) * t) * exp(-u * 20f) * 0.6f
            }
            BOOM -> {
                var y = 0f
                mk(0.9f) { t -> y += 0.12f * (nz() - y); y * 6f * exp(-t * 5f) + sin(tp * 50f * t) * exp(-t * 4f) }
            }
            CLICK -> mk(0.05f) { t -> sin(tp * 1200f * t) * exp(-t * 90f) * 0.7f }
            DEATH -> mk(0.5f) { t -> sin(tp * (80f * t - 60f * t * t)) * exp(-t * 5f) * 0.9f + nz() * exp(-t * 20f) * 0.3f }
            else -> {
                var y = 0f
                mk(10f) { t ->
                    y += 0.05f * (nz() - y)
                    val beat = t % 2.5f
                    val b2 = beat - 0.3f
                    val hb = (sin(tp * 45f * beat) * exp(-beat * 20f) + (if (b2 > 0f) sin(tp * 45f * b2) * exp(-b2 * 20f) * 0.7f else 0f)) * 0.5f
                    sin(tp * 55f * t) * 0.35f + sin(tp * 82.4f * t) * 0.2f * (0.5f + 0.5f * sin(tp * 0.2f * t)) +
                        sin(tp * 110.8f * t) * 0.12f * (0.5f + 0.5f * sin(tp * 0.1f * t)) + y * 2f * (0.5f + 0.5f * sin(tp * 0.1f * t)) + hb
                }
            }
        }
    }

    private fun writeWav(f: File, d: ShortArray) {
        val n = d.size * 2
        val b = ByteBuffer.allocate(44 + n).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray())
        b.putInt(36 + n)
        b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray())
        b.putInt(16)
        b.putShort(1)
        b.putShort(1)
        b.putInt(SR)
        b.putInt(SR * 2)
        b.putShort(2)
        b.putShort(16)
        b.put("data".toByteArray())
        b.putInt(n)
        for (s in d) b.putShort(s)
        FileOutputStream(f).use { it.write(b.array()) }
    }
}
