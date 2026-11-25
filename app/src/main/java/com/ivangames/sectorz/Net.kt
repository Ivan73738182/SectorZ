package com.ivangames.sectorz

import java.io.*
import java.net.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.hypot
import kotlin.math.min

class NetPeer(val sock: Socket) {
    val din = DataInputStream(BufferedInputStream(sock.getInputStream()))
    val dout = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
    private val q = LinkedBlockingQueue<ByteArray>()
    @Volatile var alive = true
    @Volatile var jx = 0f
    @Volatile var jy = 0f
    @Volatile var ax = 0f
    @Volatile var ay = 0f
    @Volatile var atk = false
    @Volatile var wsel = 0
    @Volatile var skin = 0
    @Volatile var hello = false
    @Volatile var snap: ByteArray? = null
    var bot: Bot? = null
    var id = 0

    fun send(b: ByteArray) {
        if (q.size < 4) q.offer(b)
    }

    fun startWriter() {
        val t = Thread {
            try {
                while (alive) {
                    val b = q.take()
                    dout.writeInt(b.size)
                    dout.write(b)
                    dout.flush()
                }
            } catch (e: Exception) {
                alive = false
            }
        }
        t.isDaemon = true
        t.start()
    }

    fun close() {
        alive = false
        try { sock.close() } catch (e: Exception) { }
    }
}

class NetSync(private val g: GameView, val client: Boolean, private val ip: String, private val skin: Int) {
    @Volatile var status = ""
    private val peers = CopyOnWriteArrayList<NetPeer>()
    private var server: ServerSocket? = null
    private var me: NetPeer? = null
    private var sendT = 0f
    private var wsel = 0
    private var myId = 0
    private var ctime = 0f
    private var hostIp = ""
    private var got = false

    fun start() {
        if (client) startClient() else startHost()
    }

    fun stop() {
        try { server?.close() } catch (e: Exception) { }
        for (p in peers) p.close()
        me?.close()
    }

    fun cycleWeapon() {
        wsel = (wsel + 1) % 3
    }

    fun info(): String {
        if (client) return ""
        return "Хост. IP: " + hostIp + "   игроков: " + (1 + peers.size) + "/4"
    }

    // ---------- хост ----------

    private fun localIps(): String {
        val list = ArrayList<String>()
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val ni = en.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                val ad = ni.inetAddresses
                while (ad.hasMoreElements()) {
                    val a = ad.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress) list.add(a.hostAddress ?: "")
                }
            }
        } catch (e: Exception) { }
        return if (list.isEmpty()) "нет сети" else list.joinToString(" / ")
    }

    private fun nextId(): Int {
        for (i in 1..3) {
            var used = false
            for (p in peers) if (p.id == i) used = true
            if (!used) return i
        }
        return 1
    }

    private fun startHost() {
        hostIp = localIps()
        val t = Thread {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(47800))
                server = ss
                while (true) {
                    val s = ss.accept()
                    try {
                        if (peers.size >= 3) { s.close(); continue }
                        s.tcpNoDelay = true
                        val pr = NetPeer(s)
                        pr.id = nextId()
                        peers.add(pr)
                        pr.startWriter()
                        startPeerReader(pr)
                    } catch (e: Exception) {
                        try { s.close() } catch (x: Exception) { }
                    }
                }
            } catch (e: Exception) {
                if (server?.isClosed != true) status = "Ошибка сервера: " + (e.message ?: "")
            }
        }
        t.isDaemon = true
        t.start()
    }

    private fun startPeerReader(pr: NetPeer) {
        val t = Thread {
            try {
                while (pr.alive) {
                    val n = pr.din.readInt()
                    if (n < 1 || n > 4096) break
                    val b = ByteArray(n)
                    pr.din.readFully(b)
                    val bi = DataInputStream(ByteArrayInputStream(b))
                    val type = bi.readByte().toInt()
                    if (type == 3) {
                        pr.skin = bi.readByte().toInt().coerceIn(0, 3)
                        pr.hello = true
                    } else if (type == 1) {
                        pr.jx = bi.readFloat()
                        pr.jy = bi.readFloat()
                        pr.atk = bi.readByte().toInt() != 0
                        pr.ax = bi.readFloat()
                        pr.ay = bi.readFloat()
                        pr.wsel = bi.readByte().toInt()
                    }
                }
            } catch (e: Exception) { }
            pr.alive = false
        }
        t.isDaemon = true
        t.start()
    }

    fun hostStep(dt: Float) {
        for (pr in peers) {
            if (!pr.alive) {
                val old = pr.bot
                if (old != null) g.bots.remove(old)
                peers.remove(pr)
                pr.close()
                continue
            }
            if (!pr.hello) continue
            val bb = pr.bot
            if (bb == null) {
                val nb = Bot(g.player.x, g.player.y, Looks.players[pr.skin], true)
                nb.hp = 120f
                nb.maxHp = 120f
                nb.weapon = 3
                nb.peer = pr
                pr.bot = nb
                g.bots.add(nb)
            } else if (!g.bots.contains(bb)) {
                bb.x = g.player.x
                bb.y = g.player.y
                bb.hp = bb.maxHp
                bb.dead = false
                bb.deadT = 0f
                g.bots.add(bb)
            }
        }
        sendT += dt
        if (sendT >= 0.04f && peers.isNotEmpty()) {
            sendT = 0f
            broadcast()
        }
    }

    private fun wEnt(o: DataOutputStream, e: Ent) {
        o.writeFloat(e.x)
        o.writeFloat(e.y)
        o.writeFloat(e.angle)
        o.writeFloat(e.walk)
        o.writeFloat(e.atkT)
        var f = 0
        if (e.dead) f = f or 1
        if (e.moving) f = f or 2
        if (e.hurtT > 0f) f = f or 4
        o.writeByte(f)
        o.writeShort(e.hp.toInt().coerceIn(-1000, 30000))
        o.writeShort(e.maxHp.toInt().coerceIn(1, 30000))
    }

    private fun human(o: DataOutputStream, id: Int, sk: Int, e: Ent, weapon: Int, flash: Float, ammo: Int, kills: Int) {
        o.writeByte(id)
        o.writeByte(sk)
        wEnt(o, e)
        o.writeFloat(flash)
        o.writeByte(weapon)
        o.writeShort(ammo.coerceIn(0, 30000))
        o.writeShort(kills.coerceIn(0, 30000))
    }

    private fun buildBody(): ByteArray {
        val bo = ByteArrayOutputStream(16384)
        val o = DataOutputStream(bo)
        val p = g.player
        val hs = ArrayList<NetPeer>()
        for (pr in peers) if (pr.bot != null && pr.hello) hs.add(pr)
        o.writeByte(1 + hs.size)
        human(o, 0, skin, p, p.weapon, p.flash, p.ammo, p.kills)
        for (pr in hs) {
            val b = pr.bot
            if (b != null) human(o, pr.id, pr.skin, b, b.weapon, b.flash, 99, p.kills)
        }
        o.writeShort(g.zombies.size)
        for (z in g.zombies) {
            o.writeByte(z.type)
            o.writeByte(z.variant)
            wEnt(o, z)
        }
        o.writeShort(g.dogs.size)
        for (z in g.dogs) wEnt(o, z)
        val ai = ArrayList<Bot>()
        for (b in g.bots) if (b.peer == null) ai.add(b)
        o.writeShort(ai.size)
        for (b in ai) {
            val l = b.look
            o.writeInt(l.skin)
            o.writeInt(l.shirt)
            o.writeInt(l.pants)
            o.writeInt(l.hair)
            o.writeInt(l.hat)
            o.writeInt(l.hatColor)
            wEnt(o, b)
            o.writeFloat(b.flash)
            o.writeByte(b.weapon)
        }
        o.writeShort(g.loot.size)
        for (l in g.loot) {
            o.writeFloat(l.x)
            o.writeFloat(l.y)
            o.writeByte(l.type)
            o.writeByte(l.id)
        }
        o.writeShort(g.bullets.size)
        for (b in g.bullets) {
            o.writeFloat(b.x)
            o.writeFloat(b.y)
            o.writeFloat(b.vx)
            o.writeFloat(b.vy)
        }
        o.flush()
        return bo.toByteArray()
    }

    private fun broadcast() {
        val body = buildBody()
        for (pr in peers) {
            if (!pr.alive || !pr.hello) continue
            val msg = ByteArray(2 + body.size)
            msg[0] = 2
            msg[1] = pr.id.toByte()
            System.arraycopy(body, 0, msg, 2, body.size)
            pr.send(msg)
        }
    }

    // ---------- клиент ----------

    private fun startClient() {
        status = "Подключение к " + ip + " ..."
        val t = Thread {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, 47800), 5000)
                s.tcpNoDelay = true
                val pr = NetPeer(s)
                me = pr
                pr.startWriter()
                val bo = ByteArrayOutputStream()
                val o = DataOutputStream(bo)
                o.writeByte(3)
                o.writeByte(skin)
                pr.send(bo.toByteArray())
                status = "Ждём данные от хоста ..."
                while (pr.alive) {
                    val n = pr.din.readInt()
                    if (n < 1 || n > 300000) break
                    val b = ByteArray(n)
                    pr.din.readFully(b)
                    pr.snap = b
                    status = ""
                }
                status = "Связь потеряна"
            } catch (e: Exception) {
                status = "Нет связи: " + (e.message ?: "")
            }
        }
        t.isDaemon = true
        t.start()
    }

    private fun rEnt(i: DataInputStream, e: Ent, init: Boolean) {
        val x = i.readFloat()
        val y = i.readFloat()
        e.angle = i.readFloat()
        e.walk = i.readFloat()
        e.atkT = i.readFloat()
        val f = i.readByte().toInt()
        e.hp = i.readShort().toFloat()
        e.maxHp = i.readShort().toFloat()
        e.dead = (f and 1) != 0
        e.moving = (f and 2) != 0
        e.hurtT = if ((f and 4) != 0) 0.1f else 0f
        e.wx = x
        e.wy = y
        if (init) { e.x = x; e.y = y }
    }

    private fun takeBot(old: ArrayList<Bot>, ally: Boolean, sk: Int, shirt: Int, pants: Int, hair: Int, hat: Int, hc: Int): Bot? {
        for (k in 0 until old.size) {
            val o = old[k]
            val l = o.look
            if (o.ally == ally && l.skin == sk && l.shirt == shirt && l.pants == pants && l.hair == hair && l.hat == hat && l.hatColor == hc) {
                old.removeAt(k)
                return o
            }
        }
        return null
    }

    private fun applySnap(b: ByteArray) {
        val i = DataInputStream(ByteArrayInputStream(b))
        i.readByte()
        myId = i.readByte().toInt()
        val nh = i.readByte().toInt()
        val oldBots = ArrayList<Bot>(g.bots)
        val newBots = ArrayList<Bot>()
        for (k in 0 until nh) {
            val id = i.readByte().toInt()
            val sk = i.readByte().toInt().coerceIn(0, 3)
            if (id == myId) {
                val p = g.player
                rEnt(i, p, false)
                p.flash = i.readFloat()
                p.weapon = i.readByte().toInt()
                p.ammo = i.readShort().toInt()
                p.kills = i.readShort().toInt()
            } else {
                val lk = Looks.players[sk]
                var bt = takeBot(oldBots, true, lk.skin, lk.shirt, lk.pants, lk.hair, lk.hat, lk.hatColor)
                var init = false
                if (bt == null) {
                    bt = Bot(0f, 0f, lk, true)
                    init = true
                }
                rEnt(i, bt, init)
                bt.flash = i.readFloat()
                bt.weapon = i.readByte().toInt()
                i.readShort()
                i.readShort()
                newBots.add(bt)
            }
        }
        val nz = i.readShort().toInt()
        for (k in 0 until nz) {
            val type = i.readByte().toInt()
            val variant = i.readByte().toInt()
            val z: Zombie
            var init = false
            if (k < g.zombies.size && g.zombies[k].type == type && g.zombies[k].variant == variant) {
                z = g.zombies[k]
            } else {
                z = Zombie(0f, 0f, type, variant)
                z.r = if (type == 0) 14f else if (type == 1) 13f else 20f
                init = true
                if (k < g.zombies.size) g.zombies[k] = z else g.zombies.add(z)
            }
            rEnt(i, z, init)
        }
        while (g.zombies.size > nz) g.zombies.removeAt(g.zombies.size - 1)
        val nd = i.readShort().toInt()
        for (k in 0 until nd) {
            val z: Dog
            var init = false
            if (k < g.dogs.size) {
                z = g.dogs[k]
            } else {
                z = Dog(0f, 0f)
                z.r = 11f
                init = true
                g.dogs.add(z)
            }
            rEnt(i, z, init)
        }
        while (g.dogs.size > nd) g.dogs.removeAt(g.dogs.size - 1)
        val nb = i.readShort().toInt()
        for (k in 0 until nb) {
            val sk = i.readInt()
            val sh = i.readInt()
            val pa = i.readInt()
            val ha = i.readInt()
            val ht = i.readInt()
            val hc = i.readInt()
            var bt = takeBot(oldBots, false, sk, sh, pa, ha, ht, hc)
            var init = false
            if (bt == null) {
                bt = Bot(0f, 0f, Look(sk, sh, pa, ha, ht, hc), false)
                init = true
            }
            rEnt(i, bt, init)
            bt.flash = i.readFloat()
            bt.weapon = i.readByte().toInt()
            newBots.add(bt)
        }
        g.bots.clear()
        g.bots.addAll(newBots)
        val nl = i.readShort().toInt()
        g.loot.clear()
        for (k in 0 until nl) {
            val x = i.readFloat()
            val y = i.readFloat()
            val t = i.readByte().toInt()
            val id = i.readByte().toInt()
            val l = Loot(x, y, t, id)
            l.t = ctime
            g.loot.add(l)
        }
        val nbul = i.readShort().toInt()
        g.bullets.clear()
        for (k in 0 until nbul) {
            val x = i.readFloat()
            val y = i.readFloat()
            val vx = i.readFloat()
            val vy = i.readFloat()
            g.bullets.add(Bullet(x, y, vx, vy, 0f, 1f, false))
        }
        if (!got) {
            got = true
            g.player.x = g.player.wx
            g.player.y = g.player.wy
        }
    }

    private fun sendInput(pr: NetPeer, jx: Float, jy: Float, atk: Boolean, ax: Float, ay: Float) {
        val bo = ByteArrayOutputStream(24)
        val o = DataOutputStream(bo)
        o.writeByte(1)
        o.writeFloat(jx)
        o.writeFloat(jy)
        o.writeByte(if (atk) 1 else 0)
        o.writeFloat(ax)
        o.writeFloat(ay)
        o.writeByte(wsel)
        pr.send(bo.toByteArray())
    }

    fun clientStep(dt: Float, jx: Float, jy: Float, atk: Boolean, ax: Float, ay: Float) {
        ctime += dt
        val pr = me ?: return
        val b = pr.snap
        if (b != null) {
            pr.snap = null
            try { applySnap(b) } catch (e: Exception) { status = "Ошибка данных" }
        }
        if (got) {
            val k = min(1f, dt * 14f)
            for (z in g.zombies) { z.x += (z.wx - z.x) * k; z.y += (z.wy - z.y) * k }
            for (z in g.dogs) { z.x += (z.wx - z.x) * k; z.y += (z.wy - z.y) * k }
            for (z in g.bots) { z.x += (z.wx - z.x) * k; z.y += (z.wy - z.y) * k }
            val p = g.player
            if (hypot(p.wx - p.x, p.wy - p.y) > 300f) { p.x = p.wx; p.y = p.wy }
            p.x += (p.wx - p.x) * k
            p.y += (p.wy - p.y) * k
        }
        for (l in g.loot) l.t += dt
        for (bl in g.bullets) { bl.x += bl.vx * dt; bl.y += bl.vy * dt }
        sendT += dt
        if (sendT >= 0.04f) {
            sendT = 0f
            sendInput(pr, jx, jy, atk, ax, ay)
        }
    }
}
