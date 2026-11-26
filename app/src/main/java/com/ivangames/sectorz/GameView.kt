package com.ivangames.sectorz

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class GameView(ctx: Context, private val skin: Int, private val multi: Boolean, private val onExit: () -> Unit) : View(ctx), Runnable {

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

    init {
        buildMap()
        reset()
    }

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

    private fun say(s: String) { msg = s; msgT = 2f }

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
        val mr = Random(7)
        val t = 22f
        val roofCols = intArrayOf(0xFF5D4037.toInt(), 0xFF455A64.toInt(), 0xFF6D4C41.toInt(), 0xFF37474F.toInt(), 0xFF7B3F3F.toInt(), 0xFF4E5D3A.toInt())
        for (cx in 0 until 8) for (cy in 0 until 8) {
            if (mr.nextFloat() < 0.12f) continue
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
        while (props.size < 90 && tries < 800) {
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
        val ids = intArrayOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 5)
        return ids[rnd.nextInt(ids.size)]
    }

    private fun randomLoot(x: Float, y: Float): Loot {
        val r = rnd.nextFloat()
        return if (r < 0.32f) Loot(x, y, 0, 0)
        else if (r < 0.55f) Loot(x, y, 1, 0)
        else Loot(x, y, 2, pickWeapon())
    }

    private fun reset() {
        zombies.clear(); dogs.clear(); bots.clear(); bullets.clear(); parts.clear(); loot.clear()
        gameOver = false
        overT = 0f
        hitFlash = 0f
        joyId = -1; atkId = -1; joyDx = 0f; joyDy = 0f
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
        say("Найди оружие и патроны в домах!")
    }

    private fun spawnZombie() {
        val pnt = freePoint(900f)
        val r = rnd.nextFloat()
        val type = if (r < 0.62f) 0 else if (r < 0.88f) 1 else 2
        val z = Zombie(pnt.x, pnt.y, type, rnd.nextInt(6))
        if (type == 0) { z.hp = 60f; z.r = 14f }
        else if (type == 1) { z.hp = 35f; z.r = 13f }
        else { z.hp = 200f; z.r = 20f }
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

    private fun hitEnemy(e: Ent, dmg: Float, ang: Float, byPlayer: Boolean) {
        if (e.dead) return
        e.hp -= dmg
        e.hurtT = 0.15f
        splat(e.x, e.y, ang, 4, 0xFF8B1A1A.toInt())
        moveEnt(e, cos(ang) * 5f, sin(ang) * 5f)
        if (e.hp <= 0f) {
            e.dead = true
            e.deadT = 0f
            e.atkT = -1f
            if (byPlayer) player.kills++
            splat(e.x, e.y, ang, 10, 0xFF6B1010.toInt())
        }
    }

    private fun hurtAlly(t: Ent, dmg: Float, ang: Float) {
        if (t.dead) return
        t.hp -= dmg
        t.hurtT = 0.2f
        splat(t.x, t.y, ang, 5, 0xFFA01818.toInt())
        if (t === player) hitFlash = 0.35f
        if (t.hp <= 0f) {
            t.dead = true
            t.deadT = 0f
            t.atkT = -1f
            if (t === player) { gameOver = true; overT = 0f }
        }
    }

    private fun fire(x: Float, y: Float, ang: Float, w: Int, mine: Boolean) {
        val a = ang + (rnd.nextFloat() - 0.5f) * 2f * W.spread[w]
        val sx = x + cos(ang) * (W.gunLen[w] + 10f)
        val sy = y + sin(ang) * (W.gunLen[w] + 10f)
        val sp = 1500f
        bullets.add(Bullet(sx, sy, cos(a) * sp, sin(a) * sp, W.dmg[w], if (w == 5) 0.7f else 0.5f, mine))
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
    }

    private fun nextWeapon() {
        val sy = sync
        if (sy != null && sy.client) { sy.cycleWeapon(); return }
        val p = player
        if (p.dead) return
        var i = p.weapon
        for (k in 0 until 6) {
            i = (i + 1) % 6
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
        if (!nearHuman(e)) return
        e.hurtT = max(0f, e.hurtT - dt)
        e.cool -= dt
        var tgt: Ent? = null
        var bd = detect * detect
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
                if (e.cool <= 0f) { e.atkT = 0f; e.atkHit = false; e.cool = cdT }
            } else steer(e, ang, speed, dt, true)
        } else wander(e, speed * 0.3f, dt)
    }

    private fun updateZombie(z: Zombie, dt: Float) {
        if (z.type == 0) updateMonster(z, dt, 52f, 520f, 10f, 0.55f, 1.0f, 8f)
        else if (z.type == 1) updateMonster(z, dt, 100f, 600f, 7f, 0.4f, 0.7f, 8f)
        else updateMonster(z, dt, 36f, 480f, 24f, 0.7f, 1.4f, 14f)
    }

    private fun updateBot(b: Bot, dt: Float) {
        val pr = b.peer
        if (pr != null) { updateRemote(b, pr, dt); return }
        b.hurtT = max(0f, b.hurtT - dt)
        b.cool -= dt
        b.flash -= dt
        if (b.dead) { b.deadT += dt; return }
        val en = nearestEnemy(b.x, b.y, 440f)
        if (en != null) {
            val ang = atan2(en.y - b.y, en.x - b.x)
            turn(b, ang, 4f, dt)
            val dist = hypot(en.x - b.x, en.y - b.y)
            if (b.cool <= 0f && abs(normAng(ang - b.angle)) < 0.12f) {
                fire(b.x, b.y, b.angle + (rnd.nextFloat() - 0.5f) * 0.3f, b.weapon, false)
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
            else -> {
                val id = l.id
                if (p.owned[id]) {
                    if (id >= 3) { p.ammo += 15; say("+15 патронов"); true } else { false }
                } else {
                    p.owned[id] = true
                    p.weapon = id
                    if (id >= 3) p.ammo += 12
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
        if (p.dead) { p.deadT += dt; p.moving = false; return }
        val mag = hypot(joyDx, joyDy)
        p.moving = mag > 0.15f
        var moveAng = p.angle
        if (p.moving) {
            moveAng = atan2(joyDy, joyDx)
            val sp = 175f * min(1f, mag)
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
                    fire(p.x, p.y, p.angle, p.weapon, true)
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
                    val hit = hitTest(b)
                    if (hit != null) {
                        hitEnemy(hit, b.dmg, atan2(b.vy, b.vx), b.mine)
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
            if (pickup(l)) it.remove()
        }
    }

    private fun update(dt: Float) {
        if (sync?.client == true) { updateClientView(dt); return }
        sync?.hostStep(dt)
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
        if (spawnT > 2.5f) {
            spawnT = 0f
            if (zombies.size < 80) spawnZombie()
            if (dogs.size < 18 && rnd.nextInt(3) == 0) spawnDog()
        }
        for (b in buildings) {
            val inside = player.x > b.x && player.x < b.x + b.w && player.y > b.y && player.y < b.y + b.h - 4f
            val target = if (inside) 0f else 1f
            b.roofA += (target - b.roofA) * min(1f, dt * 8f)
        }
        camX += (player.x - camX) * min(1f, dt * 10f)
        camY += (player.y - camY) * min(1f, dt * 10f)
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
        val v = vignette
        if (v != null) c.drawRect(0f, 0f, w, h, v)
        if (hitFlash > 0f) c.drawColor(Color.argb(min(160, (hitFlash * 400f).toInt()), 200, 0, 0))
        drawHud(c, w, h)
    }

    private fun vis(e: Ent, x0: Float, y0: Float, x1: Float, y1: Float): Boolean =
        e.x > x0 - 40f && e.x < x1 + 40f && e.y > y0 - 40f && e.y < y1 + 40f

    private fun drawZ(c: Canvas, z: Zombie) {
        c.save()
        c.translate(z.x, z.y)
        Sprites.zombie(c, z.type, z.variant, z.angle, z.walk, z.atkT, if (z.dead) 2 else if (z.hurtT > 0f) 1 else 0, z.moving)
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
        Sprites.human(c, b.look, b.angle, b.walk, b.moving, -1f, b.flash, b.weapon, if (b.dead) 2 else if (b.hurtT > 0f) 1 else 0)
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
        for (b in bullets) {
            c.drawLine(b.x, b.y, b.x - b.vx * 0.025f, b.y - b.vy * 0.025f, strokeP(0xFFFFE680.toInt(), 3f))
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
        c.drawCircle(x0 + player.x * k, y0 + player.y * k, 3.2f * d, fillP(0xFFFFFFFF.toInt()))
        c.drawRect(x0, y0, x0 + sz, y0 + sz, strokeP(0xAAFFFFFF.toInt(), 1.5f * d))
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        val p = player
        val bx = 16f * d
        val by = 14f * d
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
        if (msgT > 0f) txt(c, msg, w / 2f, 56f * d, 18f * d, 0xFFFFEB3B.toInt(), Paint.Align.CENTER)
        drawMini(c, w)
        val st = sync?.status
        if (!st.isNullOrEmpty()) txt(c, st, w / 2f, h * 0.5f, 22f * d, 0xFFFFEB3B.toInt(), Paint.Align.CENTER)
        val inf = sync?.info()
        if (!inf.isNullOrEmpty()) txt(c, inf, w / 2f, h - 14f * d, 13f * d, 0xFFB0BEC5.toInt(), Paint.Align.CENTER)

        if (joyId != -1) {
            c.drawCircle(joyBX, joyBY, 70f * d, fillP(0x22FFFFFF))
            c.drawCircle(joyBX, joyBY, 70f * d, strokeP(0x66FFFFFF, 2f * d))
            c.drawCircle(joyCX, joyCY, 30f * d, fillP(0x88FFFFFF.toInt()))
        } else {
            c.drawCircle(120f * d, h - 120f * d, 60f * d, strokeP(0x33FFFFFF, 2f * d))
        }
        val pressed = atkId != -1
        c.drawCircle(atX, atY, atR, fillP(if (pressed) 0xCCE53935.toInt() else 0x88E53935.toInt()))
        c.drawCircle(atX, atY, atR, strokeP(0xAAFFFFFF.toInt(), 2f * d))
        if (atkId != -1) c.drawCircle(atX + atkDx * atR * 2f, atY + atkDy * atR * 2f, 16f * d, fillP(0xCCFFFFFF.toInt()))
        txt(c, if (W.isGun(p.weapon)) "ОГОНЬ" else "УДАР", atX, atY + 6f * d, 17f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)
        c.drawCircle(swX, swY, swR, fillP(0x88455A64.toInt()))
        c.drawCircle(swX, swY, swR, strokeP(0xAAFFFFFF.toInt(), 2f * d))
        txt(c, "СМЕНА", swX, swY + 4f * d, 11f * d, 0xFFFFFFFF.toInt(), Paint.Align.CENTER)

        if (gameOver && overT > 0.8f) {
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
        val r = 70f * d
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
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
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
                joyId = -1; atkId = -1; joyDx = 0f; joyDy = 0f
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
        joyId = -1; atkId = -1; joyDx = 0f; joyDy = 0f
    }

    private fun moveAtk(x: Float, y: Float) {
        val r = atR * 2f
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

    private fun updateRemote(b: Bot, pr: NetPeer, dt: Float) {
        b.hurtT = max(0f, b.hurtT - dt)
        b.cool -= dt
        b.flash -= dt
        if (b.dead) {
            b.deadT += dt
            b.moving = false
            if (b.deadT > 6f) {
                b.dead = false
                b.hp = b.maxHp
                b.deadT = 0f
                b.x = player.x
                b.y = player.y
            }
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
        val ws = intArrayOf(3, 4, 5)
        b.weapon = ws[pr.wsel.coerceIn(0, 2)]
        if (pr.atk && b.cool <= 0f) {
            fire(b.x, b.y, b.angle, b.weapon, true)
            b.flash = 0.07f
            b.cool = W.cd[b.weapon]
        }
    }

    private fun updateClientView(dt: Float) {
        time += dt
        if (hitFlash > 0f) hitFlash -= dt
        if (msgT > 0f) msgT -= dt
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
        camX += (player.x - camX) * min(1f, dt * 10f)
        camY += (player.y - camY) * min(1f, dt * 10f)
    }
}
