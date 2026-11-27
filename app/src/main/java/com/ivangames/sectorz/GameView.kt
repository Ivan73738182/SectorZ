package com.ivangames.sectorz

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class GameView(ctx: Context, private val skin: Int, private val multi: Boolean, gm: Int, private val onExit: () -> Unit) : View(ctx), Runnable {

    companion object {
        const val WORLD = 4800f
        const val CELL = 600f
        const val G = 200f
        const val GN = 24
    }

    private val d = resources.displayMetrics.density
    private val rnd = Random(System.nanoTime())
    private val walls = ArrayList<Wall>()
    private val grid = Array(GN * GN) { ArrayList<Wall>() }
    private val props = ArrayList<Prop>()
    private val buildings = ArrayList<Building>()
    private val decals = ArrayList<Decal>()
    val loot = ArrayList<Loot>()
    val zombies = ArrayList<Zombie>()
    val dogs = ArrayList<Dog>()
    val bots = ArrayList<Bot>()
    val bullets = ArrayList<Bullet>()
    private val parts = ArrayList<Particle>()
    lateinit var player: Player

    private var running = false
    private var last = 0L
    private var time = 0f
    private var camX = 0f
    private var camY = 0f
    private var spawnT = 0f
    private var hitFlash = 0f
    private var msg = ""
    private var msgT = 0f
    private var gameOver = false
    private var overT = 0f
    private var atkDx = 0f
    private var atkDy = 0f
    var sync: NetSync? = null
    private var clientHp = 100f
    private var uiMode = 0
    private var editOnly = false
    private var selEl = -1
    private var dragging = false
    private var dragDx = 0f
    private var dragDy = 0f
    private var joyFixed = false
    private var joyHX = 0f
    private var joyHY = 0f
    private var joyR = 70f
    private val cxs = FloatArray(3)
    private val cys = FloatArray(3)
    private val css = floatArrayOf(1f, 1f, 1f)
    private val uiR = Array(6) { RectF() }
    private var mbX = 0f
    private var mbY = 0f
    private var mbR = 0f
    var gmode = gm
    val myName: String = ctx.getSharedPreferences("sectorz", Context.MODE_PRIVATE).getString("name", "Игрок") ?: "Игрок"
    private var fpsV = 60f
    val evId = IntArray(48)
    val evX = FloatArray(48)
    val evY = FloatArray(48)
    var evN = 0
    var wave = 0
    private var waveT = 4f
    var zoneX = 2400f
    var zoneY = 2400f
    var zoneR = 3400f
    var zoneT = 15f
    var pvpOver = false
    private var pvpT = 0f
    private var zStep = 0
    private var zAnim = 1f
    private var zTX = 2400f
    private var zTY = 2400f
    private var zTR = 3400f
    private var zFX = 2400f
    private var zFY = 2400f
    private var zFR = 3400f
    private var specIdx = 0
    private val specNext = RectF()
    private val zonePath = Path()

    private var joyId = -1
    private var joyBX = 0f
    private var joyBY = 0f
    private var joyCX = 0f
    private var joyCY = 0f
    private var joyDx = 0f
    private var joyDy = 0f
    private var atkId = -1
    private var atX = 0f
    private var atY = 0f
    private var atR = 0f
    private var swX = 0f
    private var swY = 0f
    private var swR = 0f

    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val rf = RectF()
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0x88C8B64A.toInt()
        pathEffect = DashPathEffect(floatArrayOf(34f, 26f), 0f)
    }
    private var vignette: Paint? = null
    private val retryR = RectF()
    private val menuR = RectF()


    // ---------- вспомогательное ----------

    private fun fillP(col: Int): Paint { pt.style = Paint.Style.FILL; pt.color = col; return pt }
    private fun strokeP(col: Int, w: Float): Paint { pt.style = Paint.Style.STROKE; pt.color = col; pt.strokeWidth = w; return pt }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, align: Paint.Align) {
        tp.textSize = size
        tp.textAlign = align
        tp.color = 0xAA000000.toInt()
        c.drawText(s, x + 2f, y + 2f, tp)
        tp.color = col
        c.drawText(s, x, y, tp)
    }

    fun say(s: String) { msg = s; msgT = 2f }

    // ---------- карта ----------

    private fun addWall(w: Wall) {
        walls.add(w)
        val x0 = (w.x / G).toInt().coerceIn(0, GN - 1)
        val x1 = ((w.x + w.w) / G).toInt().coerceIn(0, GN - 1)
        val y0 = (w.y / G).toInt().coerceIn(0, GN - 1)
        val y1 = ((w.y + w.h) / G).toInt().coerceIn(0, GN - 1)
        for (gx in x0..x1) for (gy in y0..y1) grid[gy * GN + gx].add(w)
    }

    private fun buildMap() {
        walls.clear(); for (gl in grid) gl.clear(); props.clear(); buildings.clear(); decals.clear()
        val mr = Random(7 + gmode * 100)
        val skipP = if (gmode == 1) 0.5f else if (gmode == 2) 0.04f else 0.12f
        val maxP = if (gmode == 1) 170 else if (gmode == 2) 120 else 90
        val t = 22f
        val roofCols = intArrayOf(0xFF5D4037.toInt(), 0xFF455A64.toInt(), 0xFF6D4C41.toInt(), 0xFF37474F.toInt(), 0xFF7B3F3F.toInt(), 0xFF4E5D3A.toInt())
        for (cx in 0 until 8) for (cy in 0 until 8) {
            if (mr.nextFloat() < skipP) continue
            val bw = 300f + mr.nextInt(90)
            val bh = 260f + mr.nextInt(90)
            val bx = cx * CELL + 130f + mr.nextInt((470f - bw).toInt())
            val by = cy * CELL + 130f + mr.nextInt((470f - bh).toInt())
            var doorX = bx + bw / 2f - 48f + (mr.nextInt(120) - 60)
            doorX = doorX.coerceIn(bx + t + 10f, bx + bw - t - 10f - 96f)
            addWall(Wall(bx, by, bw, t))
            addWall(Wall(bx, by, t, bh))
            addWall(Wall(bx + bw - t, by, t, bh))
            addWall(Wall(bx, by + bh - t, doorX - bx, t))
            addWall(Wall(doorX + 96f, by + bh - t, bx + bw - doorX - 96f, t))
            if (mr.nextBoolean()) addWall(Wall(bx + bw / 2f - t / 2f, by + t, t, bh * 0.45f))
            buildings.add(Building(bx, by, bw, bh, doorX, roofCols[mr.nextInt(roofCols.size)]))
        }
        val carCols = intArrayOf(0xFF8E2A2A.toInt(), 0xFF2A4E8E.toInt(), 0xFF6B6B6B.toInt(), 0xFF8E7B2A.toInt(), 0xFF2A6B4A.toInt(), 0xFF3A3A3A.toInt())
        var tries = 0
        while (props.size < maxP && tries < 1200) {
            tries++
            val horiz = mr.nextBoolean()
            val kind = mr.nextInt(3)
            var pw = 34f
            var ph = 34f
            if (kind != 2) {
                if (horiz) { pw = 78f; ph = 38f } else { pw = 38f; ph = 78f }
            }
            val px: Float
            val py: Float
            if (horiz) {
                px = mr.nextFloat() * (WORLD - 200f) + 50f
                py = mr.nextInt(8) * CELL + 30f + mr.nextFloat() * 20f
            } else {
                px = mr.nextInt(8) * CELL + 30f + mr.nextFloat() * 20f
                py = mr.nextFloat() * (WORLD - 200f) + 50f
            }
            if (hypot(px - 2460f, py - 2460f) < 160f) continue
            props.add(Prop(px, py, pw, ph, kind, carCols[mr.nextInt(carCols.size)]))
            addWall(Wall(px, py, pw, ph))
        }
        for (i in 0 until 500) {
            val x = mr.nextFloat() * WORLD
            val y = mr.nextFloat() * WORLD
            var ok = true
            for (b in buildings) {
                if (x > b.x - 20f && x < b.x + b.w + 20f && y > b.y - 20f && y < b.y + b.h + 20f) { ok = false; break }
            }
            if (!ok) continue
            val onRoad = (x % CELL) < 120f || (y % CELL) < 120f
            val kind = if (onRoad) (if (mr.nextBoolean()) 0 else 2) else mr.nextInt(3)
            decals.add(Decal(x, y, kind, 6f + mr.nextFloat() * 14f))
        }
    }

    private fun blocked(x: Float, y: Float, r: Float): Boolean {
        val gx0 = ((x - r) / G).toInt().coerceIn(0, GN - 1)
        val gx1 = ((x + r) / G).toInt().coerceIn(0, GN - 1)
        val gy0 = ((y - r) / G).toInt().coerceIn(0, GN - 1)
        val gy1 = ((y + r) / G).toInt().coerceIn(0, GN - 1)
        for (gy in gy0..gy1) for (gx in gx0..gx1) {
            val list = grid[gy * GN + gx]
            for (i in 0 until list.size) {
                val w = list[i]
                val nx = if (x < w.x) w.x else if (x > w.x + w.w) w.x + w.w else x
                val ny = if (y < w.y) w.y else if (y > w.y + w.h) w.y + w.h else y
                val dx = x - nx
                val dy = y - ny
                if (dx * dx + dy * dy < r * r) return true
            }
        }
        return false
    }

    private fun moveEnt(e: Ent, dx: Float, dy: Float) {
        val nx = (e.x + dx).coerceIn(e.r, WORLD - e.r)
        if (!blocked(nx, e.y, e.r)) e.x = nx
        val ny = (e.y + dy).coerceIn(e.r, WORLD - e.r)
        if (!blocked(e.x, ny, e.r)) e.y = ny
    }

    private fun freePoint(minDist: Float): PointF {
        for (i in 0 until 60) {
            var x: Float
            var y: Float
            if (rnd.nextFloat() < 0.3f && buildings.isNotEmpty()) {
                val b = buildings[rnd.nextInt(buildings.size)]
                x = b.x + 40f + rnd.nextFloat() * (b.w - 80f)
                y = b.y + 40f + rnd.nextFloat() * (b.h - 80f)
            } else {
                x = 60f + rnd.nextFloat() * (WORLD - 120f)
                y = 60f + rnd.nextFloat() * (WORLD - 120f)
            }
            if (blocked(x, y, 22f)) continue
            if (hypot(x - player.x, y - player.y) < minDist) continue
            return PointF(x, y)
        }
        return PointF(2460f, 2300f)
    }

    // ---------- старт игры ----------

    private fun pickWeapon(): Int {
        val ids = intArrayOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 5, 6, 6, 7, 8, 9)
        return ids[rnd.nextInt(ids.size)]
    }

    private fun randomLoot(x: Float, y: Float): Loot {
        val r = rnd.nextFloat()
        return if (r < 0.30f) Loot(x, y, 0, 0)
        else if (r < 0.50f) Loot(x, y, 1, 0)
        else if (r < 0.58f) Loot(x, y, 3, 0)
        else if (r < 0.64f) Loot(x, y, 4, 0)
        else Loot(x, y, 2, pickWeapon())
    }

    private fun reset() {
        zombies.clear(); dogs.clear(); bots.clear(); bullets.clear(); parts.clear(); loot.clear()
        gameOver = false
        overT = 0f
        hitFlash = 0f
        joyId = -1; atkId = -1; sprintId = -1; joyDx = 0f; joyDy = 0f
        player = Player(2460f, 2460f, Looks.players[skin])
        player.owned[0] = true
        player.owned[3] = true
        camX = player.x
        camY = player.y
        for (b in buildings) {
            b.roofA = 1f
            val n = 2 + rnd.nextInt(3)
            for (k in 0 until n) {
                for (tr in 0 until 10) {
                    val x = b.x + 38f + rnd.nextFloat() * (b.w - 76f)
                    val y = b.y + 38f + rnd.nextFloat() * (b.h - 76f)
                    if (!blocked(x, y, 14f)) { loot.add(randomLoot(x, y)); break }
                }
            }
        }
        for (i in 0 until 50) {
            val pnt = freePoint(0f)
            loot.add(randomLoot(pnt.x, pnt.y))
        }
        for (i in 0 until 20) {
            val pnt = freePoint(250f)
            val b = Bot(pnt.x, pnt.y, Looks.random(rnd), false)
            b.weapon = if (rnd.nextFloat() < 0.7f) 3 else 4
            b.hp = 60f; b.maxHp = 60f
            b.angle = rnd.nextFloat() * 2f * PI_F
            bots.add(b)
        }
        if (multi) {
            for (i in 0 until 4) {
                if (i == skin) continue
                val b = Bot(player.x + 30f * (i + 1), player.y + 40f, Looks.players[i], true)
                b.weapon = if (i % 2 == 0) 4 else 3
                b.hp = 120f; b.maxHp = 120f
                bots.add(b)
            }
        }
        for (i in 0 until 70) spawnZombie()
        for (i in 0 until 16) spawnDog()
        afterReset()
    }

    private fun spawnZombie() {
        val pnt = freePoint(900f)
        val r = rnd.nextFloat()
        val type = if (r < 0.50f) 0 else if (r < 0.72f) 1 else if (r < 0.82f) 2 else if (r < 0.90f) 3 else if (r < 0.96f) 4 else 5
        val z = Zombie(pnt.x, pnt.y, type, rnd.nextInt(6))
        z.hp = zHp(type); z.r = zRadius(type)
        z.maxHp = z.hp
        z.angle = rnd.nextFloat() * 2f * PI_F
        zombies.add(z)
    }

    private fun spawnDog() {
        val pnt = freePoint(900f)
        val g = Dog(pnt.x, pnt.y)
        g.hp = 40f; g.maxHp = 40f; g.r = 11f
        g.angle = rnd.nextFloat() * 2f * PI_F
        dogs.add(g)
    }

    // ---------- бой ----------

    private fun splat(x: Float, y: Float, ang: Float, n: Int, col: Int) {
        if (parts.size > 400) return
        for (i in 0 until n) {
            val a = ang + (rnd.nextFloat() - 0.5f) * 1.4f
            val sp = 60f + rnd.nextFloat() * 140f
            parts.add(Particle(x, y, cos(a) * sp, sin(a) * sp, 0.35f + rnd.nextFloat() * 0.3f, col, 2f + rnd.nextFloat() * 2f))
        }
    }

    private fun nearestEnemy(x: Float, y: Float, range: Float): Ent? {
        var best: Ent? = null
        var bd = range * range
        for (z in zombies) {
            if (z.dead) continue
            val dx = z.x - x
            val dy = z.y - y
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = z }
        }
        for (z in dogs) {
            if (z.dead) continue
            val dx = z.x - x
            val dy = z.y - y
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = z }
        }
        return best
    }

    private fun hitEnemy(e: Ent, dmg: Float, ang: Float, byPlayer: Boolean, owner: Ent? = null) {
        if (e.dead) return
        e.hp -= if (byPlayer) dmg * (1f + 0.1f * upDmg) else dmg
        e.hurtT = 0.15f
        sndAt(Snd.HIT, e.x, e.y)
        splat(e.x, e.y, ang, 4, 0xFF8B1A1A.toInt())
        moveEnt(e, cos(ang) * 5f, sin(ang) * 5f)
        if (e.hp <= 0f) {
            e.dead = true
            e.deadT = 0f
            e.atkT = -1f
            if (owner is Bot) owner.kills++ else if (byPlayer) player.kills++
            sndAt(Snd.DEATH, e.x, e.y)
            splat(e.x, e.y, ang, 10, 0xFF6B1010.toInt())
            afterDeath(e, byPlayer && owner !is Bot)
        }
    }

    private fun hurtAlly(t: Ent, dmg: Float, ang: Float) {
        if (t.dead) return
        if (t === player && driving != null) return
        t.hp -= dmg
        t.hurtT = 0.2f
        sndAt(Snd.HURT, t.x, t.y)
        splat(t.x, t.y, ang, 5, 0xFFA01818.toInt())
        if (t === player) hitFlash = 0.35f
        if (t.hp <= 0f) {
            t.dead = true
            t.deadT = 0f
            t.atkT = -1f
            if (t === player) { gameOver = true; overT = 0f }
        }
    }

    private fun fire(x: Float, y: Float, ang: Float, w: Int, mine: Boolean, owner: Ent? = null) {
        val sx = x + cos(ang) * (W.gunLen[w] + 10f)
        val sy = y + sin(ang) * (W.gunLen[w] + 10f)
        val sp = if (w == 7) 2600f else 1500f
        val lf = if (w == 7) 1.0f else if (w == 5) 0.7f else if (w == 6) 0.35f else 0.5f
        for (q in 0 until (if (w == 6) 7 else 1)) {
            val a = ang + (rnd.nextFloat() - 0.5f) * 2f * W.spread[w]
            bullets.add(Bullet(sx, sy, cos(a) * sp, sin(a) * sp, W.dmg[w], lf, mine).also { it.owner = owner })
        }
        if (owner === player) noiseT = 1.6f
        sndAt(Snd.shotFor(w), x, y)
    }

    private fun meleeCheck(p: Player, e: Ent, rng: Float, dmg: Float) {
        if (e.dead) return
        val dx = e.x - p.x
        val dy = e.y - p.y
        val dist = hypot(dx, dy)
        if (dist > rng + e.r) return
        if (abs(normAng(atan2(dy, dx) - p.angle)) > 1.2f) return
        hitEnemy(e, dmg, atan2(dy, dx), true)
    }

    private fun meleeHit() {
        val p = player
        val rng = W.range[p.weapon]
        val dmg = W.dmg[p.weapon]
        for (z in zombies) meleeCheck(p, z, rng, dmg)
        for (g in dogs) meleeCheck(p, g, rng, dmg)
        if (gmode == 2) for (o in bots) if (!o.dead) {
            val ddx = o.x - p.x
            val ddy = o.y - p.y
            if (hypot(ddx, ddy) < rng + o.r && abs(normAng(atan2(ddy, ddx) - p.angle)) < 1.2f) {
                hurtAlly(o, dmg, atan2(ddy, ddx))
                if (o.dead) p.kills++
            }
        }
    }

    private fun nextWeapon() {
        val sy = sync
        if (sy != null && sy.client) { sy.cycleWeapon(); return }
        val p = player
        if (p.dead) return
        var i = p.weapon
        for (k in 0 until 10) {
            i = (i + 1) % 10
            if (p.owned[i]) { p.weapon = i; break }
        }
        p.atkT = -1f
    }

    // ---------- ИИ ----------

    private fun turn(e: Ent, target: Float, rate: Float, dt: Float) {
        e.angle = normAng(e.angle + normAng(target - e.angle) * min(1f, rate * dt))
    }

    private fun steer(e: Ent, ang: Float, speed: Float, dt: Float, face: Boolean) {
        var a = ang
        if (e.avoidT > 0f) { e.avoidT -= dt; a += e.avoidA }
        val bx = e.x
val by = e.y
        moveEnt(e, cos(a) * speed * dt, sin(a) * speed * dt)
        val moved = hypot(e.x - bx, e.y - by)
        if (moved < speed * dt * 0.3f) {
            e.stuckT += dt
            if (e.stuckT > 0.3f) {
                e.stuckT = 0f
                e.avoidT = 0.7f
                e.avoidA = if (rnd.nextBoolean()) 1.5f else -1.5f
            }
        } else e.stuckT = 0f
        e.moving = true
        e.walk += dt * (6f + speed * 0.05f)
        if (face) turn(e, a, 12f, dt)
    }

    private fun wander(e: Ent, speed: Float, dt: Float) {
        e.wanderT -= dt
        if (e.wanderT <= 0f) {
            e.wanderT = 1.5f + rnd.nextFloat() * 3f
            e.wx = rnd.nextFloat() * 2f * PI_F
            e.wy = if (rnd.nextFloat() < 0.35f) 0f else 1f
        }
        if (e.wy > 0.5f) steer(e, e.wx, speed, dt, true) else e.moving = false
    }

    private fun updateMonster(e: Ent, dt: Float, speed: Float, detect: Float, dmg: Float, atkDur: Float, cdT: Float, reachExtra: Float) {
        if (e.dead) { e.deadT += dt; return }
        if (!e.hunt && !nearHuman(e)) return
        e.hurtT = max(0f, e.hurtT - dt)
        e.cool -= dt
        var tgt: Ent? = null
        var bd = if (e.hunt) 4000f * 4000f else detect * detMul * detect * detMul
        if (!player.dead) {
            val dx = player.x - e.x
            val dy = player.y - e.y
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; tgt = player }
        }
        for (b in bots) {
            if (b.dead) continue
            val dx = b.x - e.x
            val dy = b.y - e.y
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; tgt = b }
        }
        if (e.atkT >= 0f) {
            e.atkT += dt / atkDur
            e.moving = false
            if (!e.atkHit && e.atkT >= 0.5f) {
                e.atkHit = true
                val t2 = tgt
                if (t2 != null) {
                    val dx = t2.x - e.x
                    val dy = t2.y - e.y
                    if (hypot(dx, dy) < e.r + t2.r + reachExtra + 14f) hurtAlly(t2, dmg, atan2(dy, dx))
                }
            }
            if (e.atkT >= 1f) e.atkT = -1f
            return
        }
        val t = tgt
        if (t != null) {
            val ang = atan2(t.y - e.y, t.x - e.x)
            val dist = hypot(t.x - e.x, t.y - e.y)
            if (dist < e.r + t.r + reachExtra) {
                turn(e, ang, 14f, dt)
                e.moving = false
                if (e.cool <= 0f) { e.atkT = 0f; e.atkHit = false; e.cool = cdT; sndAt(if (e is Dog) Snd.BARK else Snd.GROAN, e.x, e.y) }
            } else steer(e, ang, speed, dt, true)
        } else wander(e, speed * 0.3f, dt)
    }

    private fun updateZombie(z: Zombie, dt: Float) {
        if (z.type == 3) { updateSpitter(z, dt); return }
        if (z.type == 4) { updateExploder(z, dt); return }
        if (z.type == 5) { updateMonster(z, dt, 88f, 700f, 14f, 0.35f, 0.9f, 8f); return }
        if (z.type == 6) { updateBoss(z, dt); return }
        if (z.type == 0) updateMonster(z, dt, 52f, 520f, 10f, 0.55f, 1.0f, 8f)
        else if (z.type == 1) updateMonster(z, dt, 100f, 600f, 7f, 0.4f, 0.7f, 8f)
        else updateMonster(z, dt, 36f, 480f, 24f, 0.7f, 1.4f, 14f)
    }

    private fun updateBot(b: Bot, dt: Float) {
        val pr = b.peer
        if (pr != null) { updateRemote2(b, pr, dt); return }
        b.hurtT = max(0f, b.hurtT - dt)
        b.cool -= dt
        b.flash -= dt
        if (b.dead) { b.deadT += dt; return }
        if (gmode == 2 && !pvpOver) {
            val zdx = b.x - zoneX
            val zdy = b.y - zoneY
            if (zdx * zdx + zdy * zdy > (zoneR - 60f) * (zoneR - 60f)) { steer(b, atan2(-zdy, -zdx), 130f, dt, true); return }
        }
        val en = if (gmode == 2) nearestFoe(b, 440f) else nearestEnemy(b.x, b.y, 440f)
        if (en != null) {
            val ang = atan2(en.y - b.y, en.x - b.x)
            turn(b, ang, 4f, dt)
            val dist = hypot(en.x - b.x, en.y - b.y)
            if (b.cool <= 0f && abs(normAng(ang - b.angle)) < 0.12f) {
                fire(b.x, b.y, b.angle + (rnd.nextFloat() - 0.5f) * 0.3f, b.weapon, false, b)
                b.flash = 0.07f
                b.cool = W.cd[b.weapon] * (if (b.weapon == 3) 2.4f else 1.8f)
            }
            if (dist < 160f) steer(b, ang + PI_F, 110f, dt, false) else b.moving = false
        } else if (b.ally && hypot(player.x - b.x, player.y - b.y) > 170f) {
            steer(b, atan2(player.y - b.y, player.x - b.x), 150f, dt, true)
        } else wander(b, 70f, dt)
    }

    // ---------- игрок ----------

    private fun pickup(l: Loot): Boolean {
        val p = player
        return when (l.type) {
            0 -> {
                val n = 12 + rnd.nextInt(14)
                p.ammo += n
                say("+" + n + " патронов")
                true
            }
            1 -> {
                if (p.hp >= p.maxHp) {
                    false
                } else {
                    p.hp = min(p.maxHp, p.hp + 40f)
                    say("Аптечка +40")
                    true
                }
            }
            3 -> {
                if (gren >= 6) false else { gren++; say("Граната +1"); true }
            }
            4 -> {
                if (molo >= 5) false else { molo++; say("Коктейль Молотова +1"); true }
            }
            else -> {
                val id = l.id
                if (p.owned[id]) {
                    if (W.isGun(id)) { p.ammo += 15; say("+15 патронов"); true } else { false }
                } else {
                    p.owned[id] = true
                    p.weapon = id
                    if (W.isGun(id)) p.ammo += 12
                    say("Найдено: " + W.names[id])
                    true
                }
            }
        }
    }

    private fun updatePlayer(dt: Float) {
        val p = player
        p.hurtT = max(0f, p.hurtT - dt)
        p.cool -= dt
        p.flash -= dt
        if (p.dead) { p.deadT += dt * reviveMul(p); p.moving = false; driving = null; return }
        if (driving != null) { driveStep(dt); return }
        val mag = hypot(joyDx, joyDy)
        p.moving = mag > 0.15f
        var moveAng = p.angle
        if (p.moving) {
            moveAng = atan2(joyDy, joyDx)
            val sp = speedFor(mag)
            moveEnt(p, cos(moveAng) * sp * dt, sin(moveAng) * sp * dt)
            p.walk += dt * (6f + sp * 0.06f)
        }
        val atk = atkId != -1
        val aiming = atk && hypot(atkDx, atkDy) > 0.3f
        if (aiming) p.angle = atan2(atkDy, atkDx)
        else if (p.moving) turn(p, moveAng, 14f, dt)
        if (atk && p.cool <= 0f && p.atkT < 0f) {
            if (W.isGun(p.weapon)) {
                if (p.ammo > 0) {
                    fire(p.x, p.y, p.angle, p.weapon, true, p)
                    p.ammo--
                    p.cool = W.cd[p.weapon]
                    p.flash = 0.07f
                } else {
                    p.cool = 0.5f
                    say("Нет патронов!")
                }
            } else {
                p.atkT = 0f
                p.atkHit = false
                sndAt(Snd.SWING, p.x, p.y)
                p.cool = W.cd[p.weapon]
            }
        }
        if (p.atkT >= 0f) {
            p.atkT += dt / 0.28f
            if (!p.atkHit && p.atkT >= 0.5f) { p.atkHit = true; meleeHit() }
            if (p.atkT >= 1f) p.atkT = -1f
        }
    }

    // ---------- обновление мира ----------

    private fun hitTest(b: Bullet): Ent? {
        for (z in zombies) {
            if (z.dead) continue
            val dx = z.x - b.x
            val dy = z.y - b.y
            val rr = z.r + 2f
            if (dx * dx + dy * dy < rr * rr) return z
        }
        for (z in dogs) {
            if (z.dead) continue
            val dx = z.x - b.x
            val dy = z.y - b.y
            val rr = z.r + 2f
            if (dx * dx + dy * dy < rr * rr) return z
        }
        return null
    }

    private fun updateBullets(dt: Float) {
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            var remove = false
            val dist = hypot(b.vx, b.vy) * dt
            val steps = max(1, (dist / 10f).toInt() + 1)
            val sx = b.vx * dt / steps
            val sy = b.vy * dt / steps
            var s = 0
            while (s < steps && !remove) {
                b.x += sx
                b.y += sy
                if (b.x < 0f || b.y < 0f || b.x > WORLD || b.y > WORLD || blocked(b.x, b.y, 0.5f)) {
                    remove = true
                    splat(b.x, b.y, atan2(-b.vy, -b.vx), 3, 0xFFFFD54F.toInt())
                } else {
                    val hit = if (b.owner is Zombie) null else hitTest(b)
                    if (hit == null && (gmode == 2 || b.owner is Zombie)) {
                        val hh = hitHuman(b)
                        if (hh != null) {
                            val was = hh.dead
                            hurtAlly(hh, b.dmg, atan2(b.vy, b.vx))
                            if (!was && hh.dead) { val o = b.owner; if (o is Bot) o.kills++ else if (o is Player) o.kills++ }
                            remove = true
                        }
                    }
                    if (hit != null) {
                        hitEnemy(hit, b.dmg, atan2(b.vy, b.vx), b.mine, b.owner)
                        remove = true
                    }
                }
                s++
            }
            b.life -= dt
            if (b.life <= 0f) remove = true
            if (remove) it.remove()
        }
    }

    private fun updateParts(dt: Float) {
        val it = parts.iterator()
        while (it.hasNext()) {
            val q = it.next()
            q.x += q.vx * dt
            q.y += q.vy * dt
            q.vx *= 0.92f
            q.vy *= 0.92f
            q.life -= dt
            if (q.life <= 0f) it.remove()
        }
    }

    private fun updateLoot(dt: Float) {
        val it = loot.iterator()
        while (it.hasNext()) {
            val l = it.next()
            l.t += dt
            if (player.dead) continue
            if (abs(l.x - player.x) > 30f || abs(l.y - player.y) > 30f) continue
            if (pickup(l)) { it.remove(); sndAt(Snd.PICKUP, player.x, player.y); missionAdd(1, 1f) }
        }
    }

    private fun update(dt: Float) {
        if ((uiMode != 0 || shopOpen) && sync == null) return
        if (sync?.client == true) { updateClientView(dt); return }
        sync?.hostStep(dt)
        reviveHost()
        modeStep(dt)
        envStep(dt)
        time += dt
        if (hitFlash > 0f) hitFlash -= dt
        if (msgT > 0f) msgT -= dt
        updatePlayer(dt)
        for (b in bots) updateBot(b, dt)
        for (z in zombies) updateZombie(z, dt)
        for (g in dogs) updateMonster(g, dt, 150f, 750f, 9f, 0.35f, 0.65f, 8f)
        updateBullets(dt)
        updateParts(dt)
        updateLoot(dt)
        zombies.removeAll { it.dead && it.deadT > 10f }
        dogs.removeAll { it.dead && it.deadT > 10f }
        bots.removeAll { it.dead && it.deadT > 15f && it.peer == null }
        spawnT += dt
        if (spawnT > 2.5f && gmode == 0) {
            spawnT = 0f
            if (zombies.size < 80) spawnZombie()
            if (dogs.size < 18 && rnd.nextInt(3) == 0) spawnDog()
        }
        for (b in buildings) {
            val inside = player.x > b.x && player.x < b.x + b.w && player.y > b.y && player.y < b.y + b.h - 4f
            val target = if (inside) 0f else 1f
            b.roofA += (target - b.roofA) * min(1f, dt * 8f)
        }
        val ct = camTarget()
        camX += (ct.x - camX) * min(1f, dt * 10f)
        camY += (ct.y - camY) * min(1f, dt * 10f)
        if (gameOver) overT += dt
    }

    // ---------- рисование ----------

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val fw = w.toFloat()
        val fh = h.toFloat()
        vignette = Paint().apply {
            shader = RadialGradient(
                fw / 2f, fh / 2f, max(fw, fh) * 0.65f,
                intArrayOf(0x00000000, 0x00000000, 0xCC000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        atR = 62f * d
        atX = fw - 120f * d
        atY = fh - 110f * d
        swR = 38f * d
        swX = atX - 145f * d
        swY = atY + 5f * d
        initLayout(fw, fh)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = h / 700f
        c.drawColor(0xFF0B0B0B.toInt())
        c.save()
        c.translate(w / 2f, h / 2f)
        c.scale(s, s)
        c.translate(-camX, -camY)
        val x0 = camX - w / 2f / s - 40f
        val x1 = camX + w / 2f / s + 40f
        val y0 = camY - h / 2f / s - 40f
        val y1 = camY + h / 2f / s + 40f
        drawWorld(c, x0, y0, x1, y1)
        c.restore()
        drawDark(c, w, h, s)
        val v = vignette
        if (v != null) c.drawRect(0f, 0f, w, h, v)
        if (hitFlash > 0f) c.drawColor(Color.argb(min(160, (hitFlash * 400f).toInt()), 200, 0, 0))
        drawHud(c, w, h)
        drawModeHud(c, w, h)
        drawStage2Hud(c, w, h)
        drawStage4Hud(c, w, h)
        drawUi(c, w, h)
    }

    private fun vis(e: Ent, x0: Float, y0: Float, x1: Float, y1: Float): Boolean =
        e.x > x0 - 40f && e.x < x1 + 40f && e.y > y0 - 40f && e.y < y1 + 40f

    private fun drawZ(c: Canvas, z: Zombie) {
        c.save()
        c.translate(z.x, z.y)
        val ghost = z.type == 5 && !z.dead
        if (ghost) c.saveLayerAlpha(-40f, -40f, 40f, 40f, 120)
        Sprites.zombie(c, z.type, z.variant, z.angle, z.walk, z.atkT, if (z.dead) 2 else if (z.hurtT > 0f) 1 else 0, z.moving)
        if (ghost) c.restore()
        c.restore()
        hpBar(c, z)
    }

    private fun drawDog(c: Canvas, g: Dog) {
        c.save()
        c.translate(g.x, g.y)
        Sprites.dog(c, g.angle, g.walk, g.atkT, if (g.dead) 2 else if (g.hurtT > 0f) 1 else 0, g.moving)
        c.restore()
        hpBar(c, g)
    }

    private fun drawBot(c: Canvas, b: Bot) {
        c.save()
        c.translate(b.x, b.y)
        Sprites.human(c, b.look, b.angle, b.walk, b.moving, b.atkT, b.flash, b.weapon, if (b.dead) 2 else if (b.hurtT > 0f) 1 else 0)
        c.restore()
    }

    private fun hpBar(c: Canvas, e: Ent) {
        if (e.dead || e.hp >= e.maxHp) return
        val bw = 26f
        val l = e.x - bw / 2f
        val t = e.y - e.r - 12f
        c.drawRect(l, t, l + bw, t + 3.5f, fillP(0xAA000000.toInt()))
        c.drawRect(l, t, l + bw * max(0f, e.hp) / e.maxHp, t + 3.5f, fillP(0xFFE53935.toInt()))
    }

    private fun drawWorld(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        c.save()
        c.clipRect(0f, 0f, WORLD, WORLD)
        c.drawColor(0xFF343D33.toInt())
        for (i in 0..8) {
            val r = i * CELL
            if (r + 120f >= y0 && r <= y1) c.drawRect(0f, r, WORLD, r + 120f, fillP(0xFF3A3D42.toInt()))
            if (r + 120f >= x0 && r <= x1) c.drawRect(r, 0f, r + 120f, WORLD, fillP(0xFF3A3D42.toInt()))
        }
        val sx = floor(max(0f, x0) / 60f) * 60f
        val sy = floor(max(0f, y0) / 60f) * 60f
        for (i in 0..7) {
            val r = i * CELL + 60f
            if (r >= y0 - 5f && r <= y1 + 5f) c.drawLine(sx, r, min(WORLD, x1), r, dash)
            if (r >= x0 - 5f && r <= x1 + 5f) c.drawLine(r, sy, r, min(WORLD, y1), dash)
        }
        for (dc in decals) {
            if (dc.x < x0 || dc.x > x1 || dc.y < y0 || dc.y > y1) continue
            if (dc.kind == 0) {
                c.drawLine(dc.x, dc.y, dc.x + dc.size, dc.y + dc.size * 0.3f, strokeP(0x66000000, 2f))
            } else if (dc.kind == 1) {
                c.drawCircle(dc.x, dc.y, dc.size, fillP(0xFF263A26.toInt()))
                c.drawCircle(dc.x - 2f, dc.y - 2f, dc.size * 0.6f, fillP(0xFF2F4A2F.toInt()))
            } else {
                rf.set(dc.x - dc.size, dc.y - dc.size * 0.5f, dc.x + dc.size, dc.y + dc.size * 0.5f)
                c.drawOval(rf, fillP(0x44223344))
            }
        }
        for (b in buildings) {
            if (b.x > x1 || b.x + b.w < x0 || b.y > y1 || b.y + b.h < y0) continue
            c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, fillP(0xFF6B5A47.toInt()))
            val lp = strokeP(0x33000000, 1.5f)
            var yy = b.y + 26f
            while (yy < b.y + b.h) {
                c.drawLine(b.x, yy, b.x + b.w, yy, lp)
                yy += 26f
            }
            c.drawRect(b.doorX, b.y + b.h - 22f, b.doorX + 96f, b.y + b.h + 4f, fillP(0xFF8A7B63.toInt()))
        }
for (l in loot) {
            if (l.x < x0 || l.x > x1 || l.y < y0 || l.y > y1) continue
            c.save()
            c.translate(l.x, l.y)
            Sprites.loot(c, l.type, l.id, l.t)
            c.restore()
        }
        for (w in walls) {
            if (w.x > x1 || w.x + w.w < x0 || w.y > y1 || w.y + w.h < y0) continue
            c.drawRect(w.x, w.y, w.x + w.w, w.y + w.h, fillP(0xFF4B4339.toInt()))
            c.drawRect(w.x, w.y, w.x + w.w, w.y + w.h, strokeP(0xFF2A241E.toInt(), 3f))
        }
        for (pr in props) {
            if (pr.x > x1 || pr.x + pr.w < x0 || pr.y > y1 || pr.y + pr.h < y0) continue
            if (pr.kind == 2) {
                c.drawRect(pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, fillP(0xFF8A6A3B.toInt()))
                c.drawRect(pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, strokeP(0xFF4E3A1E.toInt(), 3f))
                c.drawLine(pr.x, pr.y, pr.x + pr.w, pr.y + pr.h, strokeP(0xFF4E3A1E.toInt(), 2f))
                c.drawLine(pr.x + pr.w, pr.y, pr.x, pr.y + pr.h, strokeP(0xFF4E3A1E.toInt(), 2f))
            } else {
                rf.set(pr.x, pr.y, pr.x + pr.w, pr.y + pr.h)
                c.drawRoundRect(rf, 10f, 10f, fillP(pr.color))
                if (pr.w > pr.h) {
                    c.drawRect(pr.x + pr.w * 0.55f, pr.y + 5f, pr.x + pr.w * 0.8f, pr.y + pr.h - 5f, fillP(0xFF1A2530.toInt()))
                    c.drawRect(pr.x + pr.w * 0.15f, pr.y + 5f, pr.x + pr.w * 0.3f, pr.y + pr.h - 5f, fillP(0xFF1A2530.toInt()))
                } else {
                    c.drawRect(pr.x + 5f, pr.y + pr.h * 0.55f, pr.x + pr.w - 5f, pr.y + pr.h * 0.8f, fillP(0xFF1A2530.toInt()))
                    c.drawRect(pr.x + 5f, pr.y + pr.h * 0.15f, pr.x + pr.w - 5f, pr.y + pr.h * 0.3f, fillP(0xFF1A2530.toInt()))
                }
                rf.set(pr.x, pr.y, pr.x + pr.w, pr.y + pr.h)
                c.drawRoundRect(rf, 10f, 10f, strokeP(0x88000000.toInt(), 2.5f))
            }
        }
        for (z in zombies) if (z.dead && vis(z, x0, y0, x1, y1)) drawZ(c, z)
        for (g in dogs) if (g.dead && vis(g, x0, y0, x1, y1)) drawDog(c, g)
        for (b in bots) if (b.dead && vis(b, x0, y0, x1, y1)) drawBot(c, b)
        for (g in dogs) if (!g.dead && vis(g, x0, y0, x1, y1)) drawDog(c, g)
        for (z in zombies) if (!z.dead && vis(z, x0, y0, x1, y1)) drawZ(c, z)
        for (b in bots) if (!b.dead && vis(b, x0, y0, x1, y1)) drawBot(c, b)
        c.save()
        c.translate(player.x, player.y)
        val pm = if (player.dead) 2 else if (player.hurtT > 0f) 1 else 0
        Sprites.human(c, player.look, player.angle, player.walk, player.moving, player.atkT, player.flash, player.weapon, pm)
        c.restore()
        if (atkId != -1) c.drawLine(player.x, player.y, player.x + cos(player.angle) * 240f, player.y + sin(player.angle) * 240f, strokeP(0x55FF5252, 2f))
        drawFx(c)
        drawFx2(c)
        drawNames(c)
        for (b in bullets) {
            c.drawLine(b.x, b.y, b.x - b.vx * 0.025f, b.y - b.vy * 0.025f, strokeP(if (b.owner is Zombie) 0xFF76FF03.toInt() else 0xFFFFE680.toInt(), 3f))
        }
        for (q in parts) {
            c.drawCircle(q.x, q.y, q.size, fillP(q.color))
        }
        for (b in buildings) {
            if (b.roofA < 0.03f) continue
            if (b.x > x1 || b.x + b.w < x0 || b.y > y1 || b.y + b.h < y0) continue
            val a = (b.roofA * 255f).toInt()
            pt.style = Paint.Style.FILL
            pt.color = b.roofColor
            pt.alpha = a
            c.drawRect(b.x - 8f, b.y - 8f, b.x + b.w + 8f, b.y + b.h + 8f, pt)
            pt.style = Paint.Style.STROKE
            pt.strokeWidth = 4f
            pt.color = 0xFF000000.toInt()
            pt.alpha = (a * 0.35f).toInt()
            c.drawRect(b.x - 8f, b.y - 8f, b.x + b.w + 8f, b.y + b.h + 8f, pt)
            pt.color = 0xFFFFFFFF.toInt()
            pt.alpha = (a * 0.12f).toInt()
            c.drawLine(b.x - 8f, b.y + b.h / 2f, b.x + b.w + 8f, b.y + b.h / 2f, pt)
        }
        if (gmode == 2 && zoneR > 0f) {
            zonePath.reset()
            zonePath.fillType = Path.FillType.EVEN_ODD
            zonePath.addRect(-200f, -200f, WORLD + 200f, WORLD + 200f, Path.Direction.CW)
            zonePath.addCircle(zoneX, zoneY, zoneR, Path.Direction.CW)
            c.drawPath(zonePath, fillP(0x55D32F2F))
            c.drawCircle(zoneX, zoneY, zoneR, strokeP(0xFFFF5252.toInt(), 6f))
        }
        c.drawRect(0f, 0f, WORLD, WORLD, strokeP(0xFF8B0000.toInt(), 16f))
        c.restore()
    }

    private fun drawMini(c: Canvas, w: Float) {
        val sz = 130f * d
        val x0 = w - sz - 12f * d
        val y0 = 12f * d
        val k = sz / WORLD
        c.drawRect(x0, y0, x0 + sz, y0 + sz, fillP(0x99000000.toInt()))
        for (b in buildings) {
            c.drawRect(x0 + b.x * k, y0 + b.y * k, x0 + (b.x + b.w) * k, y0 + (b.y + b.h) * k, fillP(0xFF6B6B6B.toInt()))
        }
        val rr = 1.6f * d
        for (z in zombies) if (!z.dead) c.drawCircle(x0 + z.x * k, y0 + z.y * k, rr, fillP(0xFFE53935.toInt()))
        for (g in dogs) if (!g.dead) c.drawCircle(x0 + g.x * k, y0 + g.y * k, rr, fillP(0xFFFF9800.toInt()))
        for (b in bots) if (!b.dead) c.drawCircle(x0 + b.x * k, y0 + b.y * k, rr, fillP(if (b.ally) 0xFF40C4FF.toInt() else 0xFF66BB6A.toInt()))
        if (gmode == 2) {
            c.save()
            c.clipRect(x0, y0, x0 + sz, y0 + sz)
            c.drawCircle(x0 + zoneX * k, y0 + zoneY * k, zoneR * k, strokeP(0xFFFF5252.toInt(), 1.5f * d))
            c.restore()
        }
        c.drawCircle(x0 + player.x * k, y0 + player.y * k, 3.2f * d, fillP(0xFFFFFFFF.toInt()))
        c.drawRect(x0, y0, x0 + sz, y0 + sz, strokeP(0xAAFFFFFF.toInt(), 1.5f * d))
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        val p = player
        val bx = 16f * d
        val by = 60f * d
        val bw = 190f * d
        val bh = 18f * d
        rf.set(bx, by, bx + bw, by + bh)
        c.drawRoundRect(rf, 6f * d, 6f * d, fillP(0x88000000.toInt()))
        rf.set(bx, by, bx + bw * (max(0f, p.hp) / p.maxHp), by + bh)
        c.drawRoundRect(rf, 6f * d, 6f * d, fillP(if (p.hp > 35f) 0xFFD32F2F.toInt() else 0xFFFF1744.toInt()))
        txt(c, "HP " + max(0, p.hp.toInt()), bx + 6f * d, by + bh - 4f * d, 12f * d, 0xFFFFFFFF.toInt(), Paint.Align.LEFT)
        var ty = by + bh + 22f * d
        txt(c, W.names[p.weapon] + (if (W.isGun(p.weapon)) "   патроны: " + p.ammo else ""), bx, ty, 15f * d, 0xFFFFFFFF.toInt(), Paint.Align.LEFT)
        ty += 20f * d
        txt(c, "Убито: " + p.kills, bx, ty, 14f * d, 0xFFFFCDD2.toInt(), Paint.Align.LEFT)
        ty += 19f * d
        val alive = bots.count { !it.dead && !it.ally }
        txt(c, "Выжившие: " + alive + "/20", bx, ty, 14f * d, 0xFFC8E6C9.toInt(), Paint.Align.LEFT)
        val bd = sync?.board
        if (bd != null) {
            for (ln in bd) {
                ty += 17f * d
                txt(c, ln, bx, ty, 12f * d, 0xFFB3E5FC.toInt(), Paint.Align.LEFT)
            }
        }
        if (msgT > 0f) txt(c, msg, w / 2f, 56f * d, 18f * d, 0xFFFFEB3B.toInt(), Paint.Align.CENTER)
        drawMini(c, w)
        val st = sync?.status
        if (!st.isNullOrEmpty()) txt(c, st, w / 2f, h * 0.5f, 22f * d, 0xFFFFEB3B.toInt(), Paint.Align.CENTER)
        val inf = sync?.info()
        if (!inf.isNullOrEmpty()) txt(c, inf, w / 2f, h - 14f * d, 13f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)

        if (joyId != -1) {
            c.drawCircle(joyBX, joyBY, joyR, fillP(0x22FFFFFF))
            c.drawCircle(joyBX, joyBY, joyR, strokeP(0x66FFFFFF, 2f * d))
            c.drawCircle(joyCX, joyCY, joyR * 0.43f, fillP(0x88FFFFFF.toInt()))
        } else {
            c.drawCircle(joyHX, joyHY, joyR * 0.86f, strokeP(0x33FFFFFF, 2f * d))
        }
        val pressed = atkId != -1
        c.drawCircle(atX, atY, atR, fillP(if (pressed) 0xCCE53935.toInt() else 0x88E53935.toInt()))
        c.drawCircle(atX, atY, atR, strokeP(0xAAFFFFFF.toInt(), 2f * d))
        if (atkId != -1) c.drawCircle(atX + atkDx * atR * 2f / Cfg.aim, atY + atkDy * atR * 2f / Cfg.aim, 16f * d, fillP(0xCCFFFFFF.toInt()))
        txt(c, if (W.isGun(p.weapon)) "ОГОНЬ" else "УДАР", atX, atY + 6f * d, 17f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        c.drawCircle(swX, swY, swR, fillP(0x88455A64.toInt()))
        c.drawCircle(swX, swY, swR, strokeP(0xAAFFFFFF.toInt(), 2f * d))
        txt(c, "СМЕНА", swX, swY + 4f * d, 11f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)

        if (gameOver && sync != null && gmode != 2) {
            c.drawColor(Color.argb(min(170, (overT * 300f).toInt()), 0, 0, 0))
            txt(c, "ВЫ ПОГИБЛИ", w / 2f, h * 0.38f, 40f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
            txt(c, "Возрождение через " + max(0, (6f - overT).toInt() + 1) + " с", w / 2f, h * 0.38f + 34f * d, 18f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        }
        if (gameOver && overT > 0.8f && sync == null && gmode != 2) {
            c.drawColor(Color.argb(min(190, ((overT - 0.8f) * 300f).toInt()), 0, 0, 0))
            txt(c, "ВЫ ПОГИБЛИ", w / 2f, h * 0.33f, 46f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
            txt(c, "Убито зомби и собак: " + p.kills, w / 2f, h * 0.33f + 36f * d, 18f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            retryR.set(w / 2f - 200f * d, h * 0.6f, w / 2f - 10f * d, h * 0.6f + 58f * d)
            menuR.set(w / 2f + 10f * d, h * 0.6f, w / 2f + 200f * d, h * 0.6f + 58f * d)
            rf.set(retryR)
            c.drawRoundRect(rf, 12f * d, 12f * d, fillP(0xFFB71C1C.toInt()))
            rf.set(menuR)
            c.drawRoundRect(rf, 12f * d, 12f * d, fillP(0xFF37474F.toInt()))
            txt(c, "ЗАНОВО", retryR.centerX(), retryR.centerY() + 7f * d, 20f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            txt(c, "В МЕНЮ", menuR.centerX(), menuR.centerY() + 7f * d, 20f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        }
    }

    // ---------- управление ----------

    private fun down(id: Int, x: Float, y: Float) {
        if (hypot(x - mbX, y - mbY) < mbR * 1.5f) { openUi(1); return }
        if (specUi(x, y)) return
        if (stage4Down(id, x, y)) return
        if (stageDown(id, x, y)) return
        if (!gameOver) {
            if (hypot(x - swX, y - swY) < swR * 1.4f) { nextWeapon(); return }
            if (atkId == -1 && hypot(x - atX, y - atY) < atR * 1.5f) { atkId = id; atkDx = 0f; atkDy = 0f; return }
            if (joyId == -1) {
                if (joyFixed) {
                    if (hypot(x - joyHX, y - joyHY) < joyR * 1.8f) {
                        joyId = id
                        joyBX = joyHX; joyBY = joyHY; joyCX = joyHX; joyCY = joyHY
                        joyDx = 0f; joyDy = 0f
                        moveJoy(x, y)
                    }
                } else if (x < width * 0.5f) {
                    joyId = id
                    joyBX = x; joyBY = y; joyCX = x; joyCY = y
                    joyDx = 0f; joyDy = 0f
                }
            }
            return
        }
        if (gameOver) {
            if (overT > 1f) {
                if (retryR.contains(x, y)) reset()
                else if (menuR.contains(x, y)) onExit()
            }
            return
        }
        if (x < width * 0.5f) {
            if (joyId == -1) {
                joyId = id
                joyBX = x; joyBY = y; joyCX = x; joyCY = y
                joyDx = 0f; joyDy = 0f
            }
        } else {
            if (hypot(x - swX, y - swY) < swR * 1.4f) nextWeapon()
            else if (atkId == -1 && hypot(x - atX, y - atY) < atR * 1.5f) { atkId = id; atkDx = 0f; atkDy = 0f }
        }
    }

    private fun moveJoy(x: Float, y: Float) {
        val r = joyR
        var dx = x - joyBX
        var dy = y - joyBY
        val m = hypot(dx, dy)
        if (m > r) { dx = dx / m * r; dy = dy / m * r }
        joyCX = joyBX + dx
        joyCY = joyBY + dy
        joyDx = dx / r
        joyDy = dy / r
    }

    private fun release(id: Int) {
        if (id == joyId) { joyId = -1; joyDx = 0f; joyDy = 0f }
        if (id == atkId) { atkId = -1; atkDx = 0f; atkDy = 0f }
        if (id == sprintId) sprintId = -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (uiMode != 0) return uiTouch(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    if (e.getPointerId(i) == joyId) moveJoy(e.getX(i), e.getY(i))
                    if (e.getPointerId(i) == atkId) moveAtk(e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                release(e.getPointerId(e.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                joyId = -1; atkId = -1; sprintId = -1; joyDx = 0f; joyDy = 0f
            }
        }
        return true
    }

    // ---------- цикл ----------

    override fun run() {
        if (!running) return
        val now = System.nanoTime()
        var dt = (now - last) / 1e9f
        last = now
        if (dt > 0.05f) dt = 0.05f
        if (dt > 0f) fpsV += (1f / dt - fpsV) * 0.05f
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
        joyId = -1; atkId = -1; sprintId = -1; joyDx = 0f; joyDy = 0f
    }

    private fun moveAtk(x: Float, y: Float) {
        val r = atR * 2f / Cfg.aim
        var dx = (x - atX) / r
        var dy = (y - atY) / r
        val m = hypot(dx, dy)
        if (m > 1f) { dx /= m; dy /= m }
        atkDx = dx
        atkDy = dy
    }

    fun startNet(mode: Int, ip: String) {
        if (mode != 2 && mode != 3) return
        val s = NetSync(this, mode == 3, ip, skin)
        sync = s
        if (mode == 3) {
            zombies.clear(); dogs.clear(); bots.clear(); loot.clear(); bullets.clear(); parts.clear()
        }
        s.start()
    }

    fun stopNet() {
        sync?.stop()
    }

    private fun nearHuman(e: Ent): Boolean {
        if (abs(e.x - player.x) < 1700f && abs(e.y - player.y) < 1700f) return true
        for (b in bots) {
            if (b.peer != null && !b.dead && abs(e.x - b.x) < 1700f && abs(e.y - b.y) < 1700f) return true
        }
        return false
    }

    private fun updateClientView(dt: Float) {
        time += dt
        if (hitFlash > 0f) hitFlash -= dt
        if (msgT > 0f) msgT -= dt
        val pm = hypot(joyDx, joyDy)
        player.moving = pm > 0.15f && !player.dead
        if (player.moving) {
            val ma = atan2(joyDy, joyDx)
            val msp = 175f * min(1f, pm)
            moveEnt(player, cos(ma) * msp * dt, sin(ma) * msp * dt)
            player.walk += dt * (6f + msp * 0.06f)
        }
        if (!player.dead) {
            if (atkId != -1 && hypot(atkDx, atkDy) > 0.3f) player.angle = atan2(atkDy, atkDx)
            else if (player.moving) turn(player, atan2(joyDy, joyDx), 14f, dt)
        }
        sync?.clientStep(dt, joyDx, joyDy, atkId != -1, atkDx, atkDy)
        if (player.hp < clientHp - 0.5f) hitFlash = 0.35f
        clientHp = player.hp
        if (player.dead && !gameOver) { gameOver = true; overT = 0f }
        if (!player.dead && gameOver) gameOver = false
        if (gameOver) overT += dt
        for (b in buildings) {
            val inside = player.x > b.x && player.x < b.x + b.w && player.y > b.y && player.y < b.y + b.h - 4f
            val target = if (inside) 0f else 1f
            b.roofA += (target - b.roofA) * min(1f, dt * 8f)
        }
        val ct = camTarget()
        camX += (ct.x - camX) * min(1f, dt * 10f)
        camY += (ct.y - camY) * min(1f, dt * 10f)
    }


    // ===== гости по сети =====

    private fun reviveHost() {
        val s = sync
        if (s != null && !s.client && player.dead && player.deadT > 6f && gmode != 2) {
            player.dead = false
            player.hp = player.maxHp
            player.deadT = 0f
            player.x = 2460f
            player.y = 2460f
            player.cool = 0f
            player.atkT = -1f
            gameOver = false
            overT = 0f
        }
    }

    private fun reviveBot(b: Bot) {
        b.dead = false
        b.hp = b.maxHp
        b.deadT = 0f
        b.atkT = -1f
        b.x = player.x
        b.y = player.y
    }

    private fun meleeCheckBot(b: Bot, e: Ent, rng: Float, dmg: Float) {
        if (e.dead) return
        val dx = e.x - b.x
        val dy = e.y - b.y
        val dist = hypot(dx, dy)
        if (dist > rng + e.r) return
        if (abs(normAng(atan2(dy, dx) - b.angle)) > 1.2f) return
        hitEnemy(e, dmg, atan2(dy, dx), true, b)
    }

    private fun meleeHitBot(b: Bot) {
        val rng = W.range[b.weapon]
        val dmg = W.dmg[b.weapon]
        for (z in zombies) meleeCheckBot(b, z, rng, dmg)
        for (g in dogs) meleeCheckBot(b, g, rng, dmg)
    }

    private fun cycleBot(b: Bot) {
        var i = b.weapon
        for (k in 0 until 10) {
            i = (i + 1) % 10
            if (b.owned[i]) { b.weapon = i; break }
        }
        b.atkT = -1f
    }

    private fun pickupBot(b: Bot, l: Loot): Boolean {
        return when (l.type) {
            0 -> {
                val n = 12 + rnd.nextInt(14)
                b.ammo += n
                b.say("+" + n + " патронов")
                true
            }
            1 -> {
                if (b.hp >= b.maxHp) {
                    false
                } else {
                    b.hp = min(b.maxHp, b.hp + 40f)
                    b.say("Аптечка +40")
                    true
                }
            }
            else -> {
                val id = l.id
                if (id >= 8) return false
                if (b.owned[id]) {
                    if (W.isGun(id)) { b.ammo += 15; b.say("+15 патронов"); true } else { false }
                } else {
                    b.owned[id] = true
                    b.weapon = id
                    if (W.isGun(id)) b.ammo += 12
                    b.say("Найдено: " + W.names[id])
                    true
                }
            }
        }
    }

    private fun updateRemote2(b: Bot, pr: NetPeer, dt: Float) {
        b.hurtT = max(0f, b.hurtT - dt)
        b.cool -= dt
        b.flash -= dt
        if (b.dead) {
            b.deadT += dt * reviveMul(b)
            b.moving = false
            b.atkT = -1f
            if (b.deadT > 6f && gmode != 2) reviveBot(b)
            return
        }
        val mag = hypot(pr.jx, pr.jy)
        b.moving = mag > 0.15f
        var moveAng = b.angle
        if (b.moving) {
            moveAng = atan2(pr.jy, pr.jx)
            val sp = 175f * min(1f, mag)
            moveEnt(b, cos(moveAng) * sp * dt, sin(moveAng) * sp * dt)
            b.walk += dt * (6f + sp * 0.06f)
        }
        val aiming = pr.atk && hypot(pr.ax, pr.ay) > 0.3f
        if (aiming) b.angle = atan2(pr.ay, pr.ax)
        else if (b.moving) turn(b, moveAng, 14f, dt)
        if (pr.swSeq != pr.lastSw) { pr.lastSw = pr.swSeq; cycleBot(b) }
        if (pr.atk && b.cool <= 0f && b.atkT < 0f) {
            if (W.isGun(b.weapon)) {
                if (b.ammo > 0) {
                    fire(b.x, b.y, b.angle, b.weapon, true, b)
                    b.ammo--
                    b.cool = W.cd[b.weapon]
                    b.flash = 0.07f
                } else {
                    b.cool = 0.5f
                    b.say("Нет патронов!")
                }
            } else {
                b.atkT = 0f
                b.atkHit = false
                b.cool = W.cd[b.weapon]
            }
        }
        if (b.atkT >= 0f) {
            b.atkT += dt / 0.28f
            if (!b.atkHit && b.atkT >= 0.5f) { b.atkHit = true; meleeHitBot(b) }
            if (b.atkT >= 1f) b.atkT = -1f
        }
        val it = loot.iterator()
        while (it.hasNext()) {
            val l = it.next()
            if (abs(l.x - b.x) > 30f || abs(l.y - b.y) > 30f) continue
            if (pickupBot(b, l)) it.remove()
        }
    }

    // ===== раскладка управления =====

    private fun defaultLayout(fw: Float, fh: Float) {
        cxs[0] = 120f * d / fw
        cys[0] = 1f - 120f * d / fh
        css[0] = 1f
        cxs[1] = 1f - 120f * d / fw
        cys[1] = 1f - 110f * d / fh
        css[1] = 1f
        cxs[2] = cxs[1] - 145f * d / fw
        cys[2] = cys[1] + 5f * d / fh
        css[2] = 1f
    }

    private fun initLayout(fw: Float, fh: Float) {
        defaultLayout(fw, fh)
        val pf = context.getSharedPreferences("sectorz", Context.MODE_PRIVATE)
        if (pf.contains("c1x")) {
            for (i in 0..2) {
                cxs[i] = pf.getFloat("c" + i + "x", cxs[i])
                cys[i] = pf.getFloat("c" + i + "y", cys[i])
                css[i] = pf.getFloat("c" + i + "s", css[i])
            }
            joyFixed = pf.getBoolean("joyFixed", false)
        }
        applyLayout()
    }

    private fun applyLayout() {
        val fw = width.toFloat()
        val fh = height.toFloat()
        joyHX = cxs[0] * fw
        joyHY = cys[0] * fh
        joyR = 70f * d * css[0]
        atX = cxs[1] * fw
        atY = cys[1] * fh
        atR = 62f * d * css[1]
        swX = cxs[2] * fw
        swY = cys[2] * fh
        swR = 38f * d * css[2]
        mbX = 36f * d
        mbY = 36f * d
        mbR = 22f * d
    }

    private fun ctlSave() {
        val ed = context.getSharedPreferences("sectorz", Context.MODE_PRIVATE).edit()
        for (i in 0..2) {
            ed.putFloat("c" + i + "x", cxs[i])
            ed.putFloat("c" + i + "y", cys[i])
            ed.putFloat("c" + i + "s", css[i])
        }
        ed.putBoolean("joyFixed", joyFixed)
        ed.apply()
    }

    private fun scaleSel(delta: Float) {
        if (selEl >= 0) {
            css[selEl] = (css[selEl] + delta).coerceIn(0.6f, 1.8f)
            applyLayout()
            ctlSave()
        }
    }

    // ===== меню в игре и кастомизация =====

    fun openEditor() {
        editOnly = true
        uiMode = 3
        selEl = -1
    }

    private fun openUi(m: Int) {
        uiMode = m
        joyId = -1
        atkId = -1
        joyDx = 0f
        joyDy = 0f
        atkDx = 0f
        atkDy = 0f
    }

    fun back(): Boolean {
        if (uiMode == 0) openUi(1)
        else if (uiMode == 1) uiMode = 0
        else if (uiMode == 2) uiMode = 1
        else {
            ctlSave()
            if (editOnly) onExit() else uiMode = 2
        }
        return true
    }

    private fun uiBtn(c: Canvas, r: RectF, label: String, hot: Boolean, ts: Float = 17f) {
        rf.set(r)
        c.drawRoundRect(rf, 12f * d, 12f * d, fillP(if (hot) 0xFFB71C1C.toInt() else 0xEE263238.toInt()))
        c.drawRoundRect(rf, 12f * d, 12f * d, strokeP(0xFFE53935.toInt(), 2f * d))
        txt(c, label, r.centerX(), r.centerY() + ts * 0.35f * d, ts * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
    }

    private fun drawEditor(c: Canvas, w: Float, h: Float) {
        c.drawColor(0xAA000000.toInt())
        val names = arrayOf("ДЖОЙСТИК", "ОГОНЬ", "СМЕНА")
        val rads = floatArrayOf(joyR, atR, swR)
        val xs = floatArrayOf(joyHX, atX, swX)
        val ys = floatArrayOf(joyHY, atY, swY)
        for (i in 0..2) {
            val sel = i == selEl
            c.drawCircle(xs[i], ys[i], rads[i], fillP(if (sel) 0x66FFA000 else 0x55FFFFFF))
            c.drawCircle(xs[i], ys[i], rads[i], strokeP(if (sel) 0xFFFFA000.toInt() else 0xAAFFFFFF.toInt(), (if (sel) 4f else 2f) * d))
            txt(c, names[i], xs[i], ys[i] + 5f * d, 13f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        }
        val bwid = min(120f * d, (w - 40f * d) / 5f)
        val bhh = 44f * d
        val x0 = w / 2f - bwid * 2.5f
        val labels = arrayOf("-", "+", "СБРОС", if (joyFixed) "ДЖОЙ: ФИКС." else "ДЖОЙ: ПЛАВ.", "ГОТОВО")
        for (i in 0..4) {
            uiR[i].set(x0 + i * bwid + 3f * d, 14f * d, x0 + (i + 1) * bwid - 3f * d, 14f * d + bhh)
            uiBtn(c, uiR[i], labels[i], false, 14f)
        }
        val hint = if (selEl < 0) "Нажми на элемент и тяни, чтобы передвинуть" else "Размер: " + (css[selEl] * 100f).toInt() + "%   (кнопки - и +)"
        txt(c, hint, w / 2f, 14f * d + bhh + 22f * d, 14f * d, 0xFFFFEB3B.toInt(), Paint.Align.CENTER)
    }

    private fun drawUi(c: Canvas, w: Float, h: Float) {
        if (uiMode == 0) {
            c.drawCircle(mbX, mbY, mbR, fillP(0x99000000.toInt()))
            c.drawCircle(mbX, mbY, mbR, strokeP(0xAAFFFFFF.toInt(), 2f * d))
            val lw = mbR * 0.55f
            for (k in -1..1) {
                c.drawLine(mbX - lw, mbY + k * mbR * 0.32f, mbX + lw, mbY + k * mbR * 0.32f, strokeP(0xFFFFFFFF.toInt(), 2.5f * d))
            }
            return
        }
        if (uiMode == 3) { drawEditor(c, w, h); return }
        c.drawColor(0xBB000000.toInt())
        val bw = min(w * 0.5f, 380f * d)
        val bh = min(60f * d, h * (if (uiMode == 2) 0.105f else 0.14f))
        val gp = min(14f * d, h * 0.03f)
        val n = if (uiMode == 1) 3 else 5
        var y = h / 2f - (n * bh + (n - 1) * gp) / 2f + 10f * d
        txt(c, if (uiMode == 1) "МЕНЮ" else "НАСТРОЙКИ", w / 2f, y - 18f * d, 30f * d, 0xFFE53935.toInt(), Paint.Align.CENTER)
        val labels = if (uiMode == 1) arrayOf("ПРОДОЛЖИТЬ", "НАСТРОЙКИ", "ВЫЙТИ") else arrayOf("КАСТОМИЗАЦИЯ УПРАВЛЕНИЯ", Cfg.volLabel(), Cfg.aimLabel(), Cfg.fpsLabel(), "НАЗАД")
        for (i in 0 until n) {
            uiR[i].set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh)
            uiBtn(c, uiR[i], labels[i], false)
            y += bh + gp
        }
    }

    private fun uiPress(x: Float, y: Float) {
        if (uiMode == 1) {
            if (uiR[0].contains(x, y)) uiMode = 0
            else if (uiR[1].contains(x, y)) uiMode = 2
            else if (uiR[2].contains(x, y)) onExit()
        } else if (uiMode == 2) {
            if (uiR[0].contains(x, y)) { uiMode = 3; selEl = -1 }
            else if (uiR[1].contains(x, y)) Cfg.nextVol(context)
            else if (uiR[2].contains(x, y)) Cfg.nextAim(context)
            else if (uiR[3].contains(x, y)) Cfg.nextFps(context)
            else if (uiR[4].contains(x, y)) uiMode = 1
        } else if (uiMode == 3) {
            if (uiR[0].contains(x, y)) scaleSel(-0.1f)
            else if (uiR[1].contains(x, y)) scaleSel(0.1f)
            else if (uiR[2].contains(x, y)) {
                defaultLayout(width.toFloat(), height.toFloat())
                joyFixed = false
                applyLayout()
                ctlSave()
            }
            else if (uiR[3].contains(x, y)) { joyFixed = !joyFixed; ctlSave() }
            else if (uiR[4].contains(x, y)) {
                ctlSave()
                if (editOnly) onExit() else uiMode = 2
            } else {
                selEl = -1
                val rads = floatArrayOf(joyR, atR, swR)
                val xs = floatArrayOf(joyHX, atX, swX)
                val ys = floatArrayOf(joyHY, atY, swY)
                for (i in 2 downTo 0) {
                    if (hypot(x - xs[i], y - ys[i]) < rads[i] + 8f * d) {
                        selEl = i
                        dragging = true
                        dragDx = x - xs[i]
                        dragDy = y - ys[i]
                        break
                    }
                }
            }
        }
    }

    private fun uiTouch(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> uiPress(x, y)
            MotionEvent.ACTION_MOVE -> {
                if (uiMode == 3 && dragging && selEl >= 0) {
                    cxs[selEl] = ((x - dragDx) / width).coerceIn(0.04f, 0.96f)
                    cys[selEl] = ((y - dragDy) / height).coerceIn(0.1f, 0.96f)
                    applyLayout()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) { dragging = false; ctlSave() }
            }
        }
        return true
    }



    // ===== режимы игры =====

    fun applyMode(m: Int) {
        if (m == gmode) return
        gmode = m
        buildMap()
        for (b in buildings) b.roofA = 1f
    }

    fun spawnPoint(): PointF = if (gmode == 2) freePoint(0f) else PointF(player.x, player.y)

    private fun afterReset() {
        resetWorld()
        wave = 0; waveT = 4f; pvpOver = false; specIdx = 0; pvpT = 0f; zStep = 0
        zoneX = WORLD / 2f; zoneY = WORLD / 2f; zoneR = 3400f
        zTX = zoneX; zTY = zoneY; zTR = zoneR; zFX = zoneX; zFY = zoneY; zFR = zoneR
        zAnim = 1f; zoneT = 15f
        if (gmode == 1) {
            zombies.clear(); dogs.clear(); bots.clear()
            say("Волны зомби! Держись!")
        } else if (gmode == 2) {
            while (zombies.size > 40) zombies.removeAt(zombies.size - 1)
            while (dogs.size > 10) dogs.removeAt(dogs.size - 1)
            for (b in bots) { b.hp = 100f; b.maxHp = 100f; b.weapon = 3 + rnd.nextInt(3) }
            for (i in 0 until 80) { val q = freePoint(0f); loot.add(randomLoot(q.x, q.y)) }
            val sp = freePoint(0f)
            player.x = sp.x; player.y = sp.y; camX = sp.x; camY = sp.y
            say("PVP: зона сужается, каждый сам за себя!")
        } else {
            say("Найди оружие и патроны в домах!")
        }
    }

    private fun modeStep(dt: Float) {
        if (gmode == 1) {
            var left = 0
            for (z in zombies) if (!z.dead) left++
            for (z in dogs) if (!z.dead) left++
            if (left == 0) {
                waveT -= dt
                if (waveT <= 0f) startWave()
            }
        } else if (gmode == 2) zoneStep(dt)
    }

    private fun startWave() {
        wave++
        waveT = 6f
        val n = min(70, 6 + wave * 4)
        val hs = ArrayList<Ent>()
        if (!player.dead) hs.add(player)
        for (b in bots) if (!b.dead && b.peer != null) hs.add(b)
        if (hs.isEmpty()) hs.add(player)
        for (i in 0 until n) {
            val h = hs[rnd.nextInt(hs.size)]
            var sx = h.x
            var sy = h.y
            for (t in 0 until 20) {
                val a = rnd.nextFloat() * 2f * PI_F
                val r = 650f + rnd.nextFloat() * 400f
                val x = (h.x + cos(a) * r).coerceIn(60f, WORLD - 60f)
                val y = (h.y + sin(a) * r).coerceIn(60f, WORLD - 60f)
                if (!blocked(x, y, 22f)) { sx = x; sy = y; break }
            }
            val q = rnd.nextFloat()
            if (wave >= 2 && q < 0.18f) {
                val g = Dog(sx, sy)
                g.hp = 40f + wave * 3f; g.maxHp = g.hp; g.r = 11f; g.hunt = true
                dogs.add(g)
            } else {
                val type = waveType(q)
                val z = Zombie(sx, sy, type, rnd.nextInt(6))
                val k = 1f + wave * 0.06f
                z.hp = zHp(type) * k
                z.maxHp = z.hp
                z.r = zRadius(type)
                z.angle = rnd.nextFloat() * 2f * PI_F
                z.hunt = true
                zombies.add(z)
            }
        }
        for (i in 0 until 5) {
            val h = hs[rnd.nextInt(hs.size)]
            val a = rnd.nextFloat() * 2f * PI_F
            val x = h.x + cos(a) * 160f
            val y = h.y + sin(a) * 160f
            if (!blocked(x, y, 16f)) loot.add(randomLoot(x, y))
        }
        if (wave % 5 == 0) spawnBoss()
        say("Волна " + wave + "!")
    }

    private fun zoneStep(dt: Float) {
        if (pvpOver) return
        pvpT += dt
        zAnim = min(1f, zAnim + dt / 4f)
        zoneX = lerpF(zFX, zTX, zAnim)
        zoneY = lerpF(zFY, zTY, zAnim)
        zoneR = lerpF(zFR, zTR, zAnim)
        zoneT -= dt
        if (zoneT <= 0f) {
            if (zTR > 120f) {
                zStep++
                val nr = max(120f, zTR - 260f)
                val a = rnd.nextFloat() * 2f * PI_F
                val off = rnd.nextFloat() * (zTR - nr) * 0.8f
                zFX = zoneX; zFY = zoneY; zFR = zoneR
                zTX = (zTX + cos(a) * off).coerceIn(300f, WORLD - 300f)
                zTY = (zTY + sin(a) * off).coerceIn(300f, WORLD - 300f)
                zTR = nr
                zAnim = 0f
                say("Зона сужается!")
            }
            zoneT = 10f
        }
        val dps = (3f + 1.5f * zStep) * dt
        val r2 = zoneR * zoneR
        if (!player.dead && outZ(player, r2)) hurtZone(player, dps)
        for (b in bots) if (!b.dead && outZ(b, r2)) hurtZone(b, dps)
        for (z in zombies) if (!z.dead && outZ(z, r2)) hurtZone(z, dps)
        for (z in dogs) if (!z.dead && outZ(z, r2)) hurtZone(z, dps)
        var alive = if (player.dead) 0 else 1
        for (b in bots) if (!b.dead) alive++
        if (pvpT > 5f && alive <= 1) pvpOver = true
    }

    private fun outZ(e: Ent, r2: Float): Boolean {
        val dx = e.x - zoneX
        val dy = e.y - zoneY
        return dx * dx + dy * dy > r2
    }

    private fun hurtZone(e: Ent, dmg: Float) {
        e.hp -= dmg
        e.hurtT = 0.1f
        if (e === player) hitFlash = 0.12f
        if (e.hp <= 0f && !e.dead) {
            e.dead = true
            e.deadT = 0f
            e.atkT = -1f
            if (e === player) { gameOver = true; overT = 0f }
        }
    }

    private fun nearestFoe(b: Bot, range: Float): Ent? {
        var best: Ent? = nearestEnemy(b.x, b.y, range)
        var bd = range * range
        if (best != null) bd = (best.x - b.x) * (best.x - b.x) + (best.y - b.y) * (best.y - b.y)
        if (!player.dead) {
            val dd = (player.x - b.x) * (player.x - b.x) + (player.y - b.y) * (player.y - b.y)
            if (dd < bd) { bd = dd; best = player }
        }
        for (o in bots) {
            if (o === b || o.dead) continue
            val dd = (o.x - b.x) * (o.x - b.x) + (o.y - b.y) * (o.y - b.y)
            if (dd < bd) { bd = dd; best = o }
        }
        return best
    }

    private fun hitHuman(b: Bullet): Ent? {
        if (!player.dead && b.owner !== player) {
            val dx = player.x - b.x
            val dy = player.y - b.y
            val rr = player.r + 2f
            if (dx * dx + dy * dy < rr * rr) return player
        }
        for (o in bots) {
            if (o.dead || o === b.owner) continue
            val dx = o.x - b.x
            val dy = o.y - b.y
            val rr = o.r + 2f
            if (dx * dx + dy * dy < rr * rr) return o
        }
        return null
    }

    private fun camTarget(): Ent {
        if (gmode == 2 && player.dead) {
            val l = ArrayList<Ent>()
            for (b in bots) if (!b.dead) l.add(b)
            if (l.isNotEmpty()) return l[((specIdx % l.size) + l.size) % l.size]
        }
        return player
    }

    private fun specUi(x: Float, y: Float): Boolean {
        if (gmode != 2) return false
        if (pvpOver) {
            if (sync?.client != true && retryR.contains(x, y)) { reset(); return true }
            if (menuR.contains(x, y)) { onExit(); return true }
            return true
        }
        if (player.dead) {
            if (specNext.contains(x, y)) { specIdx++; return true }
            if (menuR.contains(x, y)) { onExit(); return true }
            return true
        }
        return false
    }

    private fun drawModeHud(c: Canvas, w: Float, h: Float) {
        if (Cfg.fps) txt(c, "FPS: " + fpsV.toInt(), w / 2f, h - 30f * d, 12f * d, 0xFF9E9E9E.toInt(), Paint.Align.CENTER)
        if (gmode == 1) {
            var left = 0
            for (z in zombies) if (!z.dead) left++
            for (z in dogs) if (!z.dead) left++
            txt(c, "ВОЛНА " + wave, w / 2f, 28f * d, 22f * d, 0xFFFF8A65.toInt(), Paint.Align.CENTER)
            txt(c, if (left > 0) "Осталось: " + left else "Передышка перед следующей волной", w / 2f, 46f * d, 13f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        } else if (gmode == 2) {
            var alive = if (player.dead) 0 else 1
            for (b in bots) if (!b.dead) alive++
            txt(c, "Живых: " + alive, w / 2f, 28f * d, 20f * d, 0xFFFFCDD2.toInt(), Paint.Align.CENTER)
            txt(c, "Зона сужается через " + max(0, zoneT.toInt() + 1) + " с", w / 2f, 46f * d, 13f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            val zx = player.x - zoneX
            val zy = player.y - zoneY
            if (!player.dead && zx * zx + zy * zy > zoneR * zoneR) {
                txt(c, "ВНЕ ЗОНЫ! Беги в круг!", w / 2f, h * 0.32f, 24f * d, 0xFFFF1744.toInt(), Paint.Align.CENTER)
            }
            val bw = min(w * 0.3f, 200f * d)
            val bh = 46f * d
            if (pvpOver) {
                c.drawColor(0x99000000.toInt())
                txt(c, if (!player.dead) "ПОБЕДА!" else "ИГРА ОКОНЧЕНА", w / 2f, h * 0.38f, 44f * d, if (!player.dead) 0xFFFFD54F.toInt() else 0xFFE53935.toInt(), Paint.Align.CENTER)
                txt(c, "Убито: " + player.kills, w / 2f, h * 0.38f + 32f * d, 18f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
                retryR.set(w / 2f - bw - 8f * d, h * 0.55f, w / 2f - 8f * d, h * 0.55f + bh)
                menuR.set(w / 2f + 8f * d, h * 0.55f, w / 2f + bw + 8f * d, h * 0.55f + bh)
                if (sync?.client != true) uiBtn(c, retryR, "ЗАНОВО", false) else retryR.setEmpty()
                uiBtn(c, menuR, "ВЫЙТИ", false)
            } else if (player.dead) {
                txt(c, "НАБЛЮДЕНИЕ", w / 2f, h - 70f * d, 18f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
                specNext.set(w / 2f - bw - 8f * d, h - 58f * d, w / 2f - 8f * d, h - 58f * d + bh)
                menuR.set(w / 2f + 8f * d, h - 58f * d, w / 2f + bw + 8f * d, h - 58f * d + bh)
                uiBtn(c, specNext, "ДАЛЕЕ >", false)
                uiBtn(c, menuR, "ВЫЙТИ", false)
            }
        }
    }



    // ===== звук и имена =====

    fun sndAt(id: Int, x: Float, y: Float) {
        val v = 1f - hypot(x - camX, y - camY) / 1000f
        if (v > 0.05f) Snd.play(id, v)
        if (evN < 48 && sync != null && sync?.client != true) {
            evId[evN] = id
            evX[evN] = x
            evY[evN] = y
            evN++
        }
    }

    fun playEvent(id: Int, x: Float, y: Float) {
        val v = 1f - hypot(x - camX, y - camY) / 1000f
        if (v > 0.05f && id in 0..11) Snd.play(id, v)
    }

    private fun drawNames(c: Canvas) {
        if (!player.dead) txt(c, myName, player.x, player.y + 34f, 15f, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        for (b in bots) if (!b.dead && b.name.isNotEmpty()) txt(c, b.name, b.x, b.y + 34f, 15f, 0xFF80D8FF.toInt(), Paint.Align.CENTER)
    }


    // ===== ЭТАП 2-3: бег, гранаты, ночь, погода, скрытность, новые зомби, босс =====

    class Thrown(var x: Float, var y: Float, var vx: Float, var vy: Float, val kind: Int) { var t = 0f }
    class Fire(val x: Float, val y: Float, val r: Float) { var t = 0f }

    var dayT = 0.12f
    var weather = 0
    private var weatherT = 70f
    private var stam = 100f
    private var tired = false
    private var sprinting = false
    private var sprintId = -1
    private var gren = 2
    private var molo = 1
    private var noiseT = 0f
    private var detMul = 1f
    private var throwCd = 0f
    private val thrown = ArrayList<Thrown>()
    private val fires = ArrayList<Fire>()
    private val booms = ArrayList<FloatArray>()
    private val lightP = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lightInit = false

    private fun resetWorld() {
        thrown.clear(); fires.clear(); booms.clear()
        gren = 2; molo = 1; stam = 100f; tired = false; sprinting = false; sprintId = -1
        dayT = 0.12f; weather = 0; weatherT = 70f; noiseT = 0f; throwCd = 0f
        resetStage4()
    }

    fun zHp(t: Int): Float = if (t == 0) 60f else if (t == 1) 35f else if (t == 2) 200f else if (t == 3) 45f else if (t == 4) 40f else if (t == 5) 45f else 1200f

    fun zRadius(t: Int): Float = if (t == 0) 14f else if (t == 1) 13f else if (t == 2) 20f else if (t == 3) 13f else if (t == 4) 15f else if (t == 5) 13f else 34f

    private fun waveType(q: Float): Int =
        if (wave >= 7 && q < 0.08f) 5
        else if (wave >= 6 && q < 0.16f) 4
        else if (wave >= 4 && q < 0.26f) 3
        else if (wave >= 5 && q < 0.38f) 2
        else if (wave >= 3 && q < 0.62f) 1
        else 0

    private fun speedFor(mag: Float): Float {
        var sp = 175f * min(1f, mag)
        if (sprinting) sp *= 1.5f
        if (tired) sp *= 0.8f
        return sp
    }

    private fun envStep(dt: Float) {
        // время суток
        val old = dayT
        dayT += dt / 240f
        if (dayT >= 1f) dayT -= 1f
        if (old < 0.5f && dayT >= 0.5f && gmode == 0 && !bossAlive() && !player.dead) spawnBoss()
        // погода
        weatherT -= dt
        if (weatherT <= 0f) {
            weatherT = 60f + rnd.nextFloat() * 60f
            val r = rnd.nextFloat()
            weather = if (r < 0.5f) 0 else if (r < 0.8f) 1 else 2
            say(if (weather == 0) "Погода прояснилась" else if (weather == 1) "Начался дождь" else "Опустился туман")
        }
        // выносливость
        val want = sprintId != -1 && player.moving && !player.dead && !tired
        sprinting = want
        if (want) {
            stam -= 28f * dt
            if (stam <= 0f) { stam = 0f; tired = true; sprinting = false }
        } else {
            stam = min(100f, stam + (if (player.moving) 12f else 24f) * dt)
            if (tired && stam >= 30f) tired = false
        }
        // скрытность
        var m = 1f
        if (!player.moving) m = 0.55f
        if (sprinting) m = 1.6f
        if (noiseT > 0f) { m = max(m, 1.9f); noiseT -= dt }
        val night = 0.5f - 0.5f * cos(2f * PI_F * dayT)
        detMul = m * (1f - 0.25f * night)
        throwCd -= dt
        updateThrown(dt)
        stage4Step(dt)
    }

    private fun nearestHuman(e: Ent, range: Float): Ent? {
        var best: Ent? = null
        var bd = range * range
        if (!player.dead) {
            val dx = player.x - e.x; val dy = player.y - e.y
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = player }
        }
        for (b in bots) {
            if (b.dead) continue
            val dx = b.x - e.x; val dy = b.y - e.y
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = b }
        }
        return best
    }

    // ---------- гранаты и огонь ----------

    private fun throwItem(kind: Int) {
        if (player.dead || throwCd > 0f) return
        if (kind == 0 && gren <= 0) { say("Нет гранат"); return }
        if (kind == 1 && molo <= 0) { say("Нет коктейлей"); return }
        if (kind == 0) gren-- else molo--
        throwCd = 0.6f
        val a = player.angle
        thrown.add(Thrown(player.x + cos(a) * 18f, player.y + sin(a) * 18f, cos(a) * 620f, sin(a) * 620f, kind))
        sndAt(Snd.SWING, player.x, player.y)
    }

    private fun damageQuiet(e: Ent, dmg: Float, byPlayer: Boolean) {
        if (e.dead) return
        e.hp -= dmg
        e.hurtT = 0.12f
        if (e.hp <= 0f) {
            e.dead = true
            e.deadT = 0f
            e.atkT = -1f
            if (byPlayer) player.kills++
            sndAt(Snd.DEATH, e.x, e.y)
            splat(e.x, e.y, 0f, 8, 0xFF6B1010.toInt())
            afterDeath(e, byPlayer)
        }
    }

    private fun afterDeath(e: Ent, byPlayer: Boolean) {
        if (byPlayer) rewardKill(e)
        if (e !is Zombie) return
        if (e.type == 4) explodeAt(e.x, e.y, 125f, 55f, byPlayer, true, true)
        if (e.type == 6) {
            say("БОСС повержен!")
            for (k in 0 until 4) {
                val a = k * 1.57f
                val lx = e.x + cos(a) * 40f
                val ly = e.y + sin(a) * 40f
                if (!blocked(lx, ly, 14f)) loot.add(if (k == 0) Loot(lx, ly, 2, 6 + rnd.nextInt(4)) else randomLoot(lx, ly))
            }
        }
    }

    private fun explodeAt(x: Float, y: Float, rad: Float, dmg: Float, byPlayer: Boolean, hitM: Boolean, hitH: Boolean) {
        booms.add(floatArrayOf(x, y, 0f, rad))
        sndAt(Snd.BOOM, x, y)
        splat(x, y, 0f, 14, 0xFFFFA000.toInt())
        if (hitM) {
            for (z in zombies) {
                if (z.dead) continue
                val dd = hypot(z.x - x, z.y - y)
                if (dd < rad + z.r) damageQuiet(z, dmg * (1f - 0.6f * dd / (rad + z.r)), byPlayer)
            }
            for (g in dogs) {
                if (g.dead) continue
                val dd = hypot(g.x - x, g.y - y)
                if (dd < rad + g.r) damageQuiet(g, dmg * (1f - 0.6f * dd / (rad + g.r)), byPlayer)
            }
        }
        if (hitH) {
            if (!player.dead) {
                val dd = hypot(player.x - x, player.y - y)
                if (dd < rad + player.r) hurtAlly(player, dmg * (1f - 0.6f * dd / (rad + player.r)) * (if (byPlayer) 0.35f else 1f), atan2(player.y - y, player.x - x))
            }
            for (b in bots) {
                if (b.dead) continue
                val dd = hypot(b.x - x, b.y - y)
                if (dd < rad + b.r) hurtAlly(b, dmg * (1f - 0.6f * dd / (rad + b.r)), atan2(b.y - y, b.x - x))
            }
        }
    }

    private fun updateThrown(dt: Float) {
        val it = thrown.iterator()
        while (it.hasNext()) {
            val q = it.next()
            q.t += dt
            val f = 1f - min(1f, 1.8f * dt)
            q.vx *= f; q.vy *= f
            val nx = q.x + q.vx * dt
            val ny = q.y + q.vy * dt
            if (blocked(nx, q.y, 3f)) q.vx = -q.vx * 0.4f else q.x = nx
            if (blocked(q.x, ny, 3f)) q.vy = -q.vy * 0.4f else q.y = ny
            if (q.kind == 0 && q.t >= 1.7f) {
                explodeAt(q.x, q.y, 150f, 140f, true, true, gmode == 2)
                if (gmode != 2 && !player.dead) {
                    val dd = hypot(player.x - q.x, player.y - q.y)
                    if (dd < 150f) hurtAlly(player, 30f * (1f - dd / 150f), atan2(player.y - q.y, player.x - q.x))
                }
                it.remove()
            } else if (q.kind == 1 && q.t >= 0.9f) {
                fires.add(Fire(q.x, q.y, 92f))
                sndAt(Snd.BOOM, q.x, q.y)
                it.remove()
            }
        }
        val fi = fires.iterator()
        while (fi.hasNext()) {
            val f = fi.next()
            f.t += dt
            if (f.t >= 6f) { fi.remove(); continue }
            for (z in zombies) if (!z.dead && hypot(z.x - f.x, z.y - f.y) < f.r + z.r) damageQuiet(z, 24f * dt, true)
            for (g in dogs) if (!g.dead && hypot(g.x - f.x, g.y - f.y) < f.r + g.r) damageQuiet(g, 24f * dt, true)
            if (gmode == 2) for (b in bots) if (!b.dead && hypot(b.x - f.x, b.y - f.y) < f.r + b.r) hurtAlly(b, 12f * dt, 0f)
            if (parts.size < 300 && rnd.nextInt(3) == 0) {
                val a = rnd.nextFloat() * 2f * PI_F
                val rr = rnd.nextFloat() * f.r
                parts.add(Particle(f.x + cos(a) * rr, f.y + sin(a) * rr, 0f, -30f, 0.5f, 0xFFFF9800.toInt(), 3f))
            }
        }
        val bi = booms.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b[2] += dt
            if (b[2] > 0.35f) bi.remove()
        }
    }

    private fun drawFx(c: Canvas) {
        for (f in fires) {
            val k = 1f - f.t / 6f
            val fl = 0.9f + 0.1f * sin(f.t * 14f)
            c.drawCircle(f.x, f.y, f.r * fl, fillP(Color.argb((70 + 60 * k).toInt(), 255, 109, 0)))
            c.drawCircle(f.x, f.y, f.r * 0.6f * fl, fillP(Color.argb((90 + 80 * k).toInt(), 255, 179, 0)))
        }
        for (q in thrown) {
            if (q.kind == 0) {
                c.drawCircle(q.x, q.y, 5f, fillP(0xFF3E5B2E.toInt()))
                c.drawCircle(q.x, q.y, 5f, strokeP(0xFF1B2A12.toInt(), 1.5f))
            } else {
                c.drawCircle(q.x, q.y, 5f, fillP(0xFF8D5A2B.toInt()))
                c.drawCircle(q.x + 3f, q.y - 3f, 3f, fillP(0xFFFFB300.toInt()))
            }
        }
        for (b in booms) {
            val k = b[2] / 0.35f
            c.drawCircle(b[0], b[1], b[3] * (0.3f + 0.7f * k), fillP(Color.argb((170 * (1f - k)).toInt(), 255, 170, 40)))
            c.drawCircle(b[0], b[1], b[3] * k, strokeP(Color.argb((220 * (1f - k)).toInt(), 255, 240, 180), 3f))
        }
    }

    // ---------- кнопки и HUD ----------

    private fun sbX(): Float = width - 34f * d
    private fun sbY(i: Int): Float = 168f * d + i * 54f * d

    private fun stageDown(id: Int, x: Float, y: Float): Boolean {
        if (gameOver || sync?.client == true) return false
        for (i in 0..2) {
            if (hypot(x - sbX(), y - sbY(i)) < 30f * d) {
                if (i == 0) sprintId = id else if (i == 1) throwItem(0) else throwItem(1)
                return true
            }
        }
        return false
    }

    private fun drawStage2Hud(c: Canvas, w: Float, h: Float) {
        if (sync?.client != true) {
            val bx = 16f * d
            val by = 60f * d + 19f * d
            c.drawRoundRect(RectF(bx, by, bx + 190f * d, by + 7f * d), 3f * d, 3f * d, fillP(0x88000000.toInt()))
            c.drawRoundRect(RectF(bx, by, bx + 190f * d * stam / 100f, by + 7f * d), 3f * d, 3f * d, fillP(if (tired) 0xFFFF7043.toInt() else 0xFFFFCA28.toInt()))
            val lab = arrayOf("БЕГ", "ГРАН " + gren, "КОКТ " + molo)
            for (i in 0..2) {
                val on = (i == 0 && sprintId != -1) || (i == 1 && gren > 0) || (i == 2 && molo > 0)
                c.drawCircle(sbX(), sbY(i), 24f * d, fillP(if (i == 0 && sprintId != -1) 0xCCFFCA28.toInt() else if (on) 0x88455A64.toInt() else 0x44455A64))
                c.drawCircle(sbX(), sbY(i), 24f * d, strokeP(0xAAFFFFFF.toInt(), 2f * d))
                txt(c, lab[i], sbX(), sbY(i) + 4f * d, 10f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            }
        }
        val night = 0.5f - 0.5f * cos(2f * PI_F * dayT)
        val tl = (if (night > 0.6f) "Ночь" else if (night > 0.3f) "Сумерки" else "День") + (if (weather == 1) ", дождь" else if (weather == 2) ", туман" else "")
        val stl = if (sprinting) "Шумно" else if (noiseT > 0f) "Шумно" else if (!player.moving) "Тихо" else "Заметен"
        txt(c, tl + "  |  " + stl, 16f * d, h - 8f * d, 12f * d, 0xFFB0BEC5.toInt(), Paint.Align.LEFT)
        var boss: Zombie? = null
        for (z in zombies) if (z.type == 6 && !z.dead) { boss = z; break }
        if (boss != null) {
            val bw = w * 0.4f
            val l = w / 2f - bw / 2f
            val t = 72f * d
            c.drawRoundRect(RectF(l, t, l + bw, t + 10f * d), 4f * d, 4f * d, fillP(0xAA000000.toInt()))
            val k = (boss.hp / boss.maxHp).coerceIn(0f, 1f)
            c.drawRoundRect(RectF(l, t, l + bw * k, t + 10f * d), 4f * d, 4f * d, fillP(0xFFB71C1C.toInt()))
            txt(c, "БОСС", w / 2f, t - 3f * d, 12f * d, 0xFFFFCDD2.toInt(), Paint.Align.CENTER)
        }
    }

    // ---------- ночь, фонарик, погода ----------

    private fun initLight() {
        lightInit = true
        lightP.shader = RadialGradient(0f, 0f, 100f, intArrayOf(0xFF000000.toInt(), 0xCC000000.toInt(), 0x00000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        lightP.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private fun hole(c: Canvas, sx: Float, sy: Float, r: Float) {
        c.save()
        c.translate(sx, sy)
        val k = r / 100f
        c.scale(k, k)
        c.drawCircle(0f, 0f, 100f, lightP)
        c.restore()
    }

    private fun drawDark(c: Canvas, w: Float, h: Float, s: Float) {
        if (!lightInit) initLight()
        val night = 0.5f - 0.5f * cos(2f * PI_F * dayT)
        var da = 0.82f * night
        if (weather == 1) da += 0.12f
        if (weather == 2) da += 0.05f
        da = min(0.92f, da)
        val px = (player.x - camX) * s + w / 2f
        val py = (player.y - camY) * s + h / 2f
        if (da > 0.03f) {
            val sc = c.saveLayer(0f, 0f, w, h, null)
            c.drawColor(Color.argb((da * 255f).toInt(), 4, 6, 18))
            if (!player.dead) {
                hole(c, px, py, 95f * s)
                if (da > 0.15f) {
                    c.save()
                    c.translate(px, py)
                    c.rotate(Math.toDegrees(player.angle.toDouble()).toFloat())
                    val L = 430f * s
                    val path = Path()
                    path.moveTo(0f, 0f)
                    path.lineTo(L, -L * 0.5f)
                    path.lineTo(L, L * 0.5f)
                    path.close()
                    c.clipPath(path)
                    hole(c, 0f, 0f, L)
                    c.restore()
                }
            }
            for (f in fires) hole(c, (f.x - camX) * s + w / 2f, (f.y - camY) * s + h / 2f, f.r * 1.8f * s)
            for (b in booms) hole(c, (b[0] - camX) * s + w / 2f, (b[1] - camY) * s + h / 2f, b[3] * 1.6f * s)
            c.restoreToCount(sc)
        }
        if (weather == 2) {
            val sc = c.saveLayer(0f, 0f, w, h, null)
            c.drawColor(Color.argb(120, 176, 188, 194))
            hole(c, px, py, 150f * s)
            c.restoreToCount(sc)
        }
        if (weather == 1) {
            val p = strokeP(0x55B3E5FC, 1.5f * d)
            for (i in 0 until 90) {
                val x = (i * 97.3f + time * 60f) % w
                val y = (i * 53.7f + time * 900f) % h
                c.drawLine(x, y, x - 5f * d, y + 16f * d, p)
            }
        }
    }

    // ---------- новые зомби и босс ----------

    private fun bossAlive(): Boolean {
        for (z in zombies) if (z.type == 6 && !z.dead) return true
        return false
    }

    private fun spawnBoss() {
        var sx = player.x + 700f
        var sy = player.y
        for (t in 0 until 30) {
            val a = rnd.nextFloat() * 2f * PI_F
            val x = (player.x + cos(a) * 750f).coerceIn(80f, WORLD - 80f)
            val y = (player.y + sin(a) * 750f).coerceIn(80f, WORLD - 80f)
            if (!blocked(x, y, 36f)) { sx = x; sy = y; break }
        }
        val z = Zombie(sx, sy, 6, 0)
        z.hp = zHp(6) + wave * 120f
        z.maxHp = z.hp
        z.r = zRadius(6)
        z.hunt = true
        z.aux = 4f
        zombies.add(z)
        say("БОСС! Берегись!")
        sndAt(Snd.GROAN, sx, sy)
    }

    private fun updateSpitter(z: Zombie, dt: Float) {
        if (z.dead) { z.deadT += dt; return }
        if (!z.hunt && !nearHuman(z)) return
        z.hurtT = max(0f, z.hurtT - dt)
        z.aux -= dt
        val t = nearestHuman(z, if (z.hunt) 900f else 560f * detMul)
        if (t == null) { wander(z, 20f, dt); return }
        val ang = atan2(t.y - z.y, t.x - z.x)
        val dist = hypot(t.x - z.x, t.y - z.y)
        turn(z, ang, 8f, dt)
        if (dist > 260f) steer(z, ang, 48f, dt, true)
        else if (dist < 150f) steer(z, ang + PI_F, 55f, dt, false)
        else z.moving = false
        if (z.aux <= 0f && dist < 430f) {
            z.aux = 2.2f
            z.atkT = 0f
            val sx = z.x + cos(z.angle) * 16f
            val sy = z.y + sin(z.angle) * 16f
            bullets.add(Bullet(sx, sy, cos(z.angle) * 520f, sin(z.angle) * 520f, 12f, 1.0f, false).also { it.owner = z })
            sndAt(Snd.GROAN, z.x, z.y)
        }
        if (z.atkT >= 0f) { z.atkT += dt / 0.4f; if (z.atkT >= 1f) z.atkT = -1f }
    }

    private fun updateExploder(z: Zombie, dt: Float) {
        if (z.dead) { z.deadT += dt; return }
        if (!z.hunt && !nearHuman(z)) return
        z.hurtT = max(0f, z.hurtT - dt)
        val t = nearestHuman(z, if (z.hunt) 900f else 560f * detMul)
        if (t == null) { wander(z, 25f, dt); return }
        val ang = atan2(t.y - z.y, t.x - z.x)
        steer(z, ang, 96f, dt, true)
        if (hypot(t.x - z.x, t.y - z.y) < z.r + t.r + 12f) {
            z.dead = true
            z.deadT = 0f
            explodeAt(z.x, z.y, 125f, 55f, false, false, true)
        }
    }

    private fun updateBoss(z: Zombie, dt: Float) {
        updateMonster(z, dt, 46f, 900f, 38f, 0.8f, 1.5f, 18f)
        if (z.dead) return
        z.aux -= dt
        if (z.aux <= 0f) {
            val t = nearestHuman(z, 240f)
            if (t != null) {
                z.aux = 6f
                explodeAt(z.x, z.y, 220f, 30f, false, false, true)
            } else z.aux = 0.5f
        }
    }


    // ===== ЭТАП 4-5: монеты, магазин, ловушки, миссии, машина, чат, команда =====

    class Trap(val x: Float, val y: Float)
    class Barr(val w: Wall) { var t = 45f }
    class Car(var x: Float, var y: Float) { var a = 0f; var v = 0f; var hp = 300f }
    class ChatL(val s: String, val ts: Long)

    var coins = 0
    private var xp = 0
    private var upHp = 0
    private var upDmg = 0
    private var traps = 2
    private var barrs = 2
    private val trapL = ArrayList<Trap>()
    private val barrL = ArrayList<Barr>()
    private val cars = ArrayList<Car>()
    private var driving: Car? = null
    private var shopOpen = false
    private var chatOpen = false
    private val shopR = Array(7) { RectF() }
    private val chatR = Array(8) { RectF() }
    val chatLines = java.util.concurrent.CopyOnWriteArrayList<ChatL>()
    var chatSeq = 0
    var chatLine = ""
    private var mType = 0
    private var mTarget = 10
    private var mProg = 0f
    private var mCount = 0
    private val phrases = arrayOf("Помогите!", "Иду к вам", "Осторожно, зомби!", "Тут лут!", "Спасибо!", "Отступаем!")

    private fun loadProg() {
        val pf = context.getSharedPreferences("sectorz", Context.MODE_PRIVATE)
        coins = pf.getInt("coins", 0)
        xp = pf.getInt("xp", 0)
        upHp = pf.getInt("upHp", 0)
        upDmg = pf.getInt("upDmg", 0)
    }

    private fun saveProg() {
        context.getSharedPreferences("sectorz", Context.MODE_PRIVATE).edit()
            .putInt("coins", coins).putInt("xp", xp).putInt("upHp", upHp).putInt("upDmg", upDmg).apply()
    }

    private fun level(): Int = 1 + xp / 100

    private fun resetStage4() {
        loadProg()
        player.maxHp = 100f + upHp * 20f + (level() - 1) * 5f
        player.hp = player.maxHp
        traps = 2; barrs = 2
        trapL.clear()
        for (b in barrL) removeWall(b.w)
        barrL.clear()
        cars.clear()
        driving = null
        shopOpen = false; chatOpen = false
        for (i in 0 until 3) {
            val q = freePoint(300f)
            val cr = Car(q.x, q.y)
            cr.a = rnd.nextFloat() * 2f * PI_F
            cars.add(cr)
        }
        mCount = 0
        newMission()
    }

    private fun removeWall(w: Wall) {
        walls.remove(w)
        for (gl in grid) gl.remove(w)
    }

    // ---------- награды и миссии ----------

    private fun rewardKill(e: Ent) {
        var g = 2
        if (e is Zombie) {
            g = if (e.type == 0) 2 else if (e.type == 1) 3 else if (e.type == 2) 6 else if (e.type == 3) 4 else if (e.type == 4) 5 else if (e.type == 5) 5 else 150
        } else if (e is Dog) {
            g = 3
        }
        val old = level()
        coins += g
        xp += g * 2
        if (level() > old) {
            coins += 20
            player.maxHp += 5f
            say("Уровень " + level() + "! +20 монет")
        }
        saveProg()
        missionAdd(0, 1f)
    }

    private fun newMission() {
        mType = rnd.nextInt(3)
        mProg = 0f
        mTarget = if (mType == 0) 8 + rnd.nextInt(10) else if (mType == 1) 3 + rnd.nextInt(3) else 60 + rnd.nextInt(60)
    }

    private fun missionText(): String {
        val t = if (mType == 0) "Убей зомби" else if (mType == 1) "Собери предметы" else "Продержись (с)"
        return "Миссия: " + t + " " + mProg.toInt() + "/" + mTarget
    }

    private fun missionAdd(t: Int, amt: Float) {
        if (gmode != 0 || mType != t) return
        mProg += amt
        if (mProg >= mTarget) {
            val r = 25 + mCount * 5
            coins += r
            mCount++
            saveProg()
            say("Миссия выполнена! +" + r + " монет")
            newMission()
        }
    }

    // ---------- шаг мира ----------

    private fun stage4Step(dt: Float) {
        missionAdd(2, dt)
        val bi = barrL.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b.t -= dt
            if (b.t <= 0f) { removeWall(b.w); bi.remove() }
        }
        val ti = trapL.iterator()
        while (ti.hasNext()) {
            val t = ti.next()
            var hit = false
            for (z in zombies) if (!z.dead && hypot(z.x - t.x, z.y - t.y) < 22f + z.r) { damageQuiet(z, 80f, true); hit = true }
            for (g in dogs) if (!g.dead && hypot(g.x - t.x, g.y - t.y) < 22f + g.r) { damageQuiet(g, 80f, true); hit = true }
            if (hit) { sndAt(Snd.HIT, t.x, t.y); splat(t.x, t.y, 0f, 6, 0xFFB0BEC5.toInt()); ti.remove() }
        }
    }

    private fun reviveMul(e: Ent): Float {
        if (sync == null) return 1f
        if (e !== player && !player.dead && hypot(player.x - e.x, player.y - e.y) < 90f) return 3f
        for (b in bots) if (b.peer != null && !b.dead && b !== e && hypot(b.x - e.x, b.y - e.y) < 90f) return 3f
        return 1f
    }

    // ---------- установка ловушек, баррикад ----------

    private fun placeTrap() {
        if (player.dead || driving != null) return
        if (traps <= 0) { say("Нет ловушек"); return }
        traps--
        trapL.add(Trap(player.x + cos(player.angle) * 30f, player.y + sin(player.angle) * 30f))
        say("Ловушка поставлена")
    }

    private fun placeBarr() {
        if (player.dead || driving != null) return
        if (barrs <= 0) { say("Нет баррикад"); return }
        val a = player.angle
        val cx = player.x + cos(a) * 50f
        val cy = player.y + sin(a) * 50f
        if (blocked(cx, cy, 40f)) { say("Здесь нельзя"); return }
        val horiz = abs(sin(a)) > abs(cos(a))
        val ww = if (horiz) 70f else 18f
        val hh = if (horiz) 18f else 70f
        val wl = Wall(cx - ww / 2f, cy - hh / 2f, ww, hh)
        addWall(wl)
        barrL.add(Barr(wl))
        barrs--
    }

    // ---------- машина ----------

    private fun nearCar(): Car? {
        for (cr in cars) if (hypot(cr.x - player.x, cr.y - player.y) < 70f) return cr
        return null
    }

    private fun toggleCar() {
        val cr = driving
        if (cr != null) {
            driving = null
            player.x = cr.x + cos(cr.a + 1.57f) * 36f
            player.y = cr.y + sin(cr.a + 1.57f) * 36f
            if (blocked(player.x, player.y, player.r)) { player.x = cr.x; player.y = cr.y }
            cr.v = 0f
        } else {
            val n = nearCar() ?: return
            driving = n
            say("Ты в машине!")
        }
    }

    private fun driveStep(dt: Float) {
        val cr = driving ?: return
        val p = player
        val mag = hypot(joyDx, joyDy)
        if (mag > 0.2f) {
            val ta = atan2(joyDy, joyDx)
            val k = (abs(cr.v) / 200f).coerceIn(0.35f, 1f)
            val da = normAng(ta - cr.a)
            cr.a += da.coerceIn(-2.6f * dt * k, 2.6f * dt * k)
            cr.v = min(430f, cr.v + 340f * dt * mag)
        } else cr.v -= cr.v * min(1f, 1.6f * dt)
        val nx = cr.x + cos(cr.a) * cr.v * dt
        val ny = cr.y + sin(cr.a) * cr.v * dt
        if (blocked(nx, cr.y, 18f)) { cr.hp -= abs(cr.v) * 0.05f; cr.v *= -0.2f } else cr.x = nx
        if (blocked(cr.x, ny, 18f)) { cr.hp -= abs(cr.v) * 0.05f; cr.v *= -0.2f } else cr.y = ny
        cr.x = cr.x.coerceIn(20f, WORLD - 20f)
        cr.y = cr.y.coerceIn(20f, WORLD - 20f)
        if (abs(cr.v) > 120f) {
            noiseT = 0.5f
            for (z in zombies) if (!z.dead && hypot(z.x - cr.x, z.y - cr.y) < 26f + z.r) { damageQuiet(z, 120f, true); cr.hp -= 6f }
            for (g in dogs) if (!g.dead && hypot(g.x - cr.x, g.y - cr.y) < 26f + g.r) { damageQuiet(g, 120f, true); cr.hp -= 4f }
        }
        p.x = cr.x; p.y = cr.y; p.angle = cr.a
        p.moving = false
        if (cr.hp <= 0f) {
            driving = null
            cars.remove(cr)
            explodeAt(cr.x, cr.y, 140f, 90f, true, true, false)
            hurtAlly(player, 40f, 0f)
        }
        val ct = camTarget()
        camX += (ct.x - camX) * min(1f, dt * 10f)
        camY += (ct.y - camY) * min(1f, dt * 10f)
    }

    // ---------- чат ----------

    fun chatAdd(s: String) {
        chatLines.add(ChatL(s, System.currentTimeMillis()))
        while (chatLines.size > 6) chatLines.removeAt(0)
    }

    private fun chatBroadcast(line: String) {
        chatAdd(line)
        chatSeq = (chatSeq + 1) and 127
        chatLine = line
    }

    fun chatFrom(name: String, t: String) { chatBroadcast(name + ": " + t.take(60)) }

    private fun sendChatMsg(t: String) {
        val sy = sync
        if (sy != null && sy.client) sy.sendChat(t) else chatBroadcast(myName + ": " + t)
    }

    private fun askChat() {
        val act = context as? android.app.Activity ?: return
        act.runOnUiThread {
            val et = android.widget.EditText(act)
            et.hint = "Сообщение"
            et.filters = arrayOf<android.text.InputFilter>(android.text.InputFilter.LengthFilter(60))
            android.app.AlertDialog.Builder(act).setTitle("Чат").setView(et)
                .setPositiveButton("Отправить") { _, _ ->
                    val t = et.text.toString().trim()
                    if (t.isNotEmpty()) sendChatMsg(t)
                }
                .setNegativeButton("Отмена", null).show()
        }
    }

    // ---------- кнопки ----------

    private fun sb2X(): Float = width - 88f * d

    private fun buy(i: Int) {
        val cost = if (i == 0) 60 + 40 * upHp else if (i == 1) 80 + 50 * upDmg else if (i == 2) 25 else if (i == 3) 30 else if (i == 4) 30 else 20
        if ((i == 0 && upHp >= 5) || (i == 1 && upDmg >= 5)) { say("Максимум"); return }
        if (i == 5 && player.hp >= player.maxHp) { say("Здоровье полное"); return }
        if (coins < cost) { say("Не хватает монет"); return }
        coins -= cost
        when (i) {
            0 -> { upHp++; player.maxHp += 20f; player.hp += 20f }
            1 -> upDmg++
            2 -> gren = min(9, gren + 2)
            3 -> traps += 3
            4 -> barrs += 3
            else -> player.hp = player.maxHp
        }
        saveProg()
    }

    private fun stage4Down(id: Int, x: Float, y: Float): Boolean {
        if (shopOpen) {
            for (i in 0..6) if (shopR[i].contains(x, y)) { if (i == 6) shopOpen = false else buy(i) }
            return true
        }
        if (chatOpen) {
            for (i in 0..7) if (chatR[i].contains(x, y)) {
                if (i < 6) { sendChatMsg(phrases[i]); chatOpen = false }
                else if (i == 6) { chatOpen = false; askChat() }
                else chatOpen = false
            }
            return true
        }
        if (gameOver) return false
        val w = width.toFloat()
        val h = height.toFloat()
        val client = sync?.client == true
        if (sync != null && hypot(x - (w / 2f - 110f * d), y - (h - 30f * d)) < 26f * d) { chatOpen = true; return true }
        if (client) return false
        if (hypot(x - (w / 2f + 110f * d), y - (h - 30f * d)) < 26f * d) { shopOpen = true; return true }
        if (hypot(x - sb2X(), y - sbY(0)) < 30f * d) { placeTrap(); return true }
        if (hypot(x - sb2X(), y - sbY(1)) < 30f * d) { placeBarr(); return true }
        if ((driving != null || nearCar() != null) && hypot(x - w / 2f, y - (h - 84f * d)) < 34f * d) { toggleCar(); return true }
        return false
    }

    private fun drawStage4Hud(c: Canvas, w: Float, h: Float) {
        val client = sync?.client == true
        if (!client) {
            val lab = arrayOf("ЛОВ " + traps, "БАР " + barrs)
            for (i in 0..1) {
                c.drawCircle(sb2X(), sbY(i), 24f * d, fillP(0x88455A64.toInt()))
                c.drawCircle(sb2X(), sbY(i), 24f * d, strokeP(0xAAFFFFFF.toInt(), 2f * d))
                txt(c, lab[i], sb2X(), sbY(i) + 4f * d, 10f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            }
            c.drawCircle(w / 2f + 110f * d, h - 30f * d, 22f * d, fillP(0x88795548.toInt()))
            c.drawCircle(w / 2f + 110f * d, h - 30f * d, 22f * d, strokeP(0xAAFFFFFF.toInt(), 2f * d))
            txt(c, "МАГ", w / 2f + 110f * d, h - 26f * d, 11f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            if (driving != null || nearCar() != null) {
                c.drawCircle(w / 2f, h - 84f * d, 30f * d, fillP(0xCC1565C0.toInt()))
                c.drawCircle(w / 2f, h - 84f * d, 30f * d, strokeP(0xFFFFFFFF.toInt(), 2f * d))
                txt(c, if (driving != null) "ВЫЙТИ" else "СЕСТЬ", w / 2f, h - 80f * d, 12f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            }
            txt(c, "Ур. " + level() + "   Монеты: " + coins, 16f * d, h - 40f * d, 12f * d, 0xFFFFE082.toInt(), Paint.Align.LEFT)
            if (gmode == 0) txt(c, missionText(), 16f * d, h - 24f * d, 12f * d, 0xFFA5D6A7.toInt(), Paint.Align.LEFT)
        }
        if (sync != null) {
            c.drawCircle(w / 2f - 110f * d, h - 30f * d, 22f * d, fillP(0x8800796B.toInt()))
            c.drawCircle(w / 2f - 110f * d, h - 30f * d, 22f * d, strokeP(0xAAFFFFFF.toInt(), 2f * d))
            txt(c, "ЧАТ", w / 2f - 110f * d, h - 26f * d, 11f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            val now = System.currentTimeMillis()
            var cy = h * 0.58f
            for (l in chatLines) {
                if (now - l.ts > 10000L) continue
                txt(c, l.s, 16f * d, cy, 13f * d, 0xFFFFFFFF.toInt(), Paint.Align.LEFT)
                cy += 17f * d
            }
            val s = h / 700f
            for (b in bots) {
                if (!b.ally || b.name.isEmpty()) continue
                val sx = (b.x - camX) * s + w / 2f
                val sy = (b.y - camY) * s + h / 2f
                if (sx > 30f && sx < w - 30f && sy > 30f && sy < h - 30f) continue
                val ex = sx.coerceIn(30f * d, w - 30f * d)
                val ey = sy.coerceIn(30f * d, h - 30f * d)
                c.drawCircle(ex, ey, 9f * d, fillP(if (b.dead) 0xCCE53935.toInt() else 0xCC40C4FF.toInt()))
                txt(c, b.name.take(6), ex, ey - 12f * d, 11f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
            }
        }
        if (shopOpen) {
            c.drawColor(0xCC000000.toInt())
            val bw = min(w * 0.6f, 420f * d)
            val bh = min(40f * d, (h - 90f * d) / 7f)
            var y = (h - (7 * (bh + 6f * d))) / 2f + 14f * d
            txt(c, "МАГАЗИН   монеты: " + coins, w / 2f, y - 10f * d, 22f * d, 0xFFFFE082.toInt(), Paint.Align.CENTER)
            val names = arrayOf(
                "Здоровье +20 (ур. " + upHp + "/5)  [" + (60 + 40 * upHp) + "]",
                "Урон +10% (ур. " + upDmg + "/5)  [" + (80 + 50 * upDmg) + "]",
                "Гранаты +2  [25]", "Ловушки +3  [30]", "Баррикады +3  [30]", "Полное лечение  [20]", "ЗАКРЫТЬ"
            )
            for (i in 0..6) {
                shopR[i].set(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + bh)
                uiBtn(c, shopR[i], names[i], false, 15f)
                y += bh + 6f * d
            }
        }
        if (chatOpen) {
            c.drawColor(0xAA000000.toInt())
            val bw = min(w * 0.3f, 220f * d)
            val bh = min(40f * d, h * 0.14f)
            val x0 = w / 2f - bw - 4f * d
            var y = h / 2f - (4 * (bh + 6f * d)) / 2f
            for (i in 0..7) {
                val col = i % 2
                val row = i / 2
                chatR[i].set(x0 + col * (bw + 8f * d), y + row * (bh + 6f * d), x0 + col * (bw + 8f * d) + bw, y + row * (bh + 6f * d) + bh)
                val lb = if (i < 6) phrases[i] else if (i == 6) "НАПИСАТЬ..." else "ЗАКРЫТЬ"
                uiBtn(c, chatR[i], lb, false, 14f)
            }
        }
    }

    private fun drawFx2(c: Canvas) {
        for (t in trapL) {
            c.drawCircle(t.x, t.y, 9f, fillP(0xFF616161.toInt()))
            c.drawCircle(t.x, t.y, 9f, strokeP(0xFFCFD8DC.toInt(), 2f))
            c.drawLine(t.x - 7f, t.y, t.x + 7f, t.y, strokeP(0xFFCFD8DC.toInt(), 2f))
        }
        for (b in barrL) {
            c.drawRect(b.w.x, b.w.y, b.w.x + b.w.w, b.w.y + b.w.h, fillP(0xFF8D6E3F.toInt()))
            c.drawRect(b.w.x, b.w.y, b.w.x + b.w.w, b.w.y + b.w.h, strokeP(0xFF4E342E.toInt(), 2f))
        }
        for (cr in cars) {
            c.save()
            c.translate(cr.x, cr.y)
            c.rotate(Math.toDegrees(cr.a.toDouble()).toFloat())
            c.drawRoundRect(RectF(-27f, -15f, 27f, 15f), 6f, 6f, fillP(if (cr === driving) 0xFF1E88E5.toInt() else 0xFF455A64.toInt()))
            c.drawRect(-6f, -11f, 12f, 11f, fillP(0xFF263238.toInt()))
            c.drawRect(22f, -12f, 27f, -7f, fillP(0xFFFFF59D.toInt()))
            c.drawRect(22f, 7f, 27f, 12f, fillP(0xFFFFF59D.toInt()))
            c.drawRect(-26f, -16f, -16f, -12f, fillP(0xFF111111.toInt()))
            c.drawRect(-26f, 12f, -16f, 16f, fillP(0xFF111111.toInt()))
            c.drawRect(14f, -16f, 24f, -12f, fillP(0xFF111111.toInt()))
            c.drawRect(14f, 12f, 24f, 16f, fillP(0xFF111111.toInt()))
            c.restore()
        }
    }

    init {
        buildMap()
        reset()
    }
}
