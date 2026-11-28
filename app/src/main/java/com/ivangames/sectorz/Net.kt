package com.ivangames.sectorz

import java.io.*
import java.net.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.hypot
import kotlin.math.min

class NetPeer(inS: InputStream, outS: OutputStream, private val closer: () -> Unit) { // m15a
    constructor(sock: Socket) : this(sock.getInputStream(), sock.getOutputStream(), { try { sock.close() } catch (e: Exception) { } })
    val din = DataInputStream(BufferedInputStream(inS))
    val dout = DataOutputStream(BufferedOutputStream(outS))
    private val q = LinkedBlockingQueue<ByteArray>()
    @Volatile var alive = true
    @Volatile var jx = 0f
    @Volatile var jy = 0f
    @Volatile var ax = 0f
    @Volatile var ay = 0f
    @Volatile var atk = false
    @Volatile var swSeq = 0
    @Volatile var sprint = false // m12
    val cmds = java.util.concurrent.ConcurrentLinkedQueue<Int>()
    @Volatile var lastSw = 0
    @Volatile var gotInput = false
    @Volatile var skin = 0
    @Volatile var name = "Игрок"
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
        closer() // m15b
    }
}

object Discovery {
    fun listen(onFound: (String) -> Unit): Thread {
        val t = Thread {
            val ds = try { DatagramSocket(null) } catch (e: Exception) { null }
            if (ds != null) {
                try {
                    ds.reuseAddress = true
                    ds.bind(InetSocketAddress(47801))
                    ds.soTimeout = 500
                    val buf = ByteArray(64)
                    while (!Thread.currentThread().isInterrupted) {
                        try {
                            val pk = DatagramPacket(buf, buf.size)
                            ds.receive(pk)
                            val s = String(pk.data, 0, pk.length)
                            if (s.startsWith("SECTORZ")) onFound(pk.address.hostAddress ?: "")
                        } catch (e: SocketTimeoutException) { }
                    }
                } catch (e: Exception) { }
                try { ds.close() } catch (e: Exception) { }
            }
        }
        t.isDaemon = true
        t.start()
        return t
    }
}

class NetSync(private val g: GameView, val client: Boolean, private val ip: String, private val skin: Int, private val online: Boolean = ip == "online") { // m15c
    @Volatile var status = ""
    @Volatile var board: List<String> = emptyList()
    private val peers = CopyOnWriteArrayList<NetPeer>()
    @Volatile private var running = true
    private var server: ServerSocket? = null
    private var me: NetPeer? = null
    private var sendT = 0f
    private var swSeq = 0
    private var myId = 0
    private var ctime = 0f
    private var hostIp = ""
    private var got = false
    private var lastNote = -1
    private var lastChat = -2

    fun start() {
        if (client) startClient() else startHost()
    }

    fun stop() {
        running = false
        try { server?.close() } catch (e: Exception) { }
        for (p in peers) p.close()
        me?.close()
        if (online) Online.leave() // m15d
    }

    fun cycleWeapon() {
        swSeq = (swSeq + 1) and 127
    }

    fun info(): String {
        if (client) return "Игра по сети: " + ip
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

    private fun bcastAddrs(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val ni = en.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                for (ia in ni.interfaceAddresses) {
                    val b = ia.broadcast
                    if (b != null) out.add(b)
                }
            }
        } catch (e: Exception) { }
        try { out.add(InetAddress.getByName("255.255.255.255")) } catch (e: Exception) { }
        return out
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
        if (online) { startOnlineHost(); return } // m15e
        hostIp = localIps()
        val t = Thread {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(47800))
                server = ss
                while (running) {
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
                if (running) status = "Ошибка сервера: " + (e.message ?: "")
            }
        }
        t.isDaemon = true
        t.start()
        val bt = Thread {
            try {
                val ds = DatagramSocket()
                ds.broadcast = true
                val msg = "SECTORZ".toByteArray()
                while (running) {
                    for (a in bcastAddrs()) {
                        try { ds.send(DatagramPacket(msg, msg.size, a, 47801)) } catch (e: Exception) { }
                    }
                    Thread.sleep(1000)
                }
                ds.close()
            } catch (e: Exception) { }
        }
        bt.isDaemon = true
        bt.start()
    }

    private fun startOnlineHost() {
        hostIp = "онлайн"
        Online.onLeft = { id ->
            for (p in peers) if (p.id == id) p.alive = false
        }
        for (i in 1 until Online.n) {
            val pipe = Online.hostPipe(i)
            val pr = NetPeer(pipe.ins, pipe.outs) { pipe.closeAll() }
            pr.id = i
            peers.add(pr)
            pr.startWriter()
            startPeerReader(pr)
        }
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
                        pr.skin = bi.readByte().toInt().coerceIn(0, 7)
                        pr.name = bi.readUTF()
                        pr.hello = true
                    } else if (type == 1) {
                        pr.jx = bi.readFloat()
                        pr.jy = bi.readFloat()
                        pr.atk = bi.readByte().toInt() != 0
                        pr.ax = bi.readFloat()
                        pr.ay = bi.readFloat()
                        val sq = bi.readByte().toInt()
                        if (!pr.gotInput) { pr.lastSw = sq; pr.gotInput = true }
                        pr.swSeq = sq
                        pr.sprint = bi.readByte().toInt() != 0 // m12b
                    } else if (type == 5) {
                        pr.cmds.add(bi.readByte().toInt())
                    } else if (type == 4) {
                        g.chatFrom(pr.name, bi.readUTF())
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
                if (old != null) {
                    g.bots.remove(old)
                    g.say(Looks.names[pr.skin] + " вышел из игры")
                }
                peers.remove(pr)
                pr.close()
                continue
            }
            if (!pr.hello) continue
            val bb = pr.bot
            if (bb == null) {
                val sp = g.spawnPoint()
                val nb = Bot(sp.x, sp.y, Looks.players[pr.skin], true)
                nb.hp = 100f
                nb.maxHp = 100f
                nb.weapon = 3
                nb.owned[0] = true
                nb.owned[3] = true
                nb.ammo = 24
                nb.mags[3] = 12 // m13n
                nb.peer = pr
                nb.team = pr.id % 2
                g.placeAtBase(nb) // m14n
                nb.name = pr.name
                pr.bot = nb
                g.bots.add(nb)
                g.say(Looks.names[pr.skin] + " в игре")
            } else if (!g.bots.contains(bb)) {
                bb.x = g.player.x
                bb.y = g.player.y
                bb.hp = bb.maxHp
                bb.dead = false
                bb.deadT = 0f
                g.bots.add(bb)
            }
        }
        val lines = ArrayList<String>()
        if (peers.isNotEmpty()) {
            lines.add(g.myName + " (ты): " + g.player.kills)
            for (pr in peers) {
                val b = pr.bot
                if (b != null) lines.add(pr.name + ": " + b.kills)
            }
        }
        board = lines
        sendT += dt
        if (sendT >= 0.04f && peers.isNotEmpty()) {
            sendT = 0f
            broadcast()
        }
        if (peers.isEmpty()) g.evN = 0
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

    private fun human(o: DataOutputStream, id: Int, sk: Int, e: Ent, weapon: Int, flash: Float, ammo: Int, kills: Int, owned: BooleanArray, name: String) {
        o.writeByte(id)
        o.writeByte(sk)
        wEnt(o, e)
        o.writeFloat(flash)
        o.writeByte(weapon)
        o.writeShort(ammo.coerceIn(0, 30000))
        o.writeByte(g.magOf(e))
        o.writeByte(g.reloadOf(e)) // m13h
        o.writeShort(kills.coerceIn(0, 30000))
        var m = 0
        for (k in 0 until 10) if (owned[k]) m = m or (1 shl k)
        o.writeShort(m)
        o.writeUTF(name)
    }

    private fun buildBody(): ByteArray {
        val bo = ByteArrayOutputStream(16384)
        val o = DataOutputStream(bo)
        val p = g.player
        val hs = ArrayList<NetPeer>()
        for (pr in peers) if (pr.bot != null && pr.hello) hs.add(pr)
        o.writeByte(1 + hs.size)
        human(o, 0, skin, p, p.weapon, p.flash, p.ammo, p.kills, p.owned, g.myName)
        for (pr in hs) {
            val b = pr.bot
            if (b != null) human(o, pr.id, pr.skin, b, b.weapon, b.flash, b.ammo, b.kills, b.owned, pr.name)
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
        val en = g.evN
        o.writeByte(en)
        for (q in 0 until en) {
            o.writeByte(g.evId[q])
            o.writeFloat(g.evX[q])
            o.writeFloat(g.evY[q])
        }
        g.evN = 0
        g.netWorldOut(o) // m12d
        o.flush()
        return bo.toByteArray()
    }

    private fun broadcast() {
        val body = buildBody()
        for (pr in peers) {
            if (!pr.alive || !pr.hello) continue
            val bo = ByteArrayOutputStream(body.size + 64)
            val o = DataOutputStream(bo)
            val b = pr.bot
            o.writeByte(2)
            o.writeByte(pr.id)
            o.writeByte(if (b != null) b.noteSeq else 0)
            o.writeUTF(if (b != null) b.note else "")
            o.writeByte(g.gmode)
            o.writeShort(g.wave)
            o.writeFloat(g.zoneX)
            o.writeFloat(g.zoneY)
            o.writeFloat(g.zoneR)
            o.writeFloat(g.zoneT)
            o.writeByte(if (g.pvpOver) 1 else 0)
            o.writeByte(g.chatSeq and 127)
            o.writeUTF(g.chatLine)
            g.netPeerOut(o, b) // m12c
            o.write(body)
            o.flush()
            pr.send(bo.toByteArray())
        }
    }

    // ---------- клиент ----------

    private fun startClient() {
        status = "Подключение к " + ip + " ..."
        val t = Thread {
            try {
                val pr: NetPeer // m15f
                if (online) pr = Online.guestPeer() else {
                    val s = Socket()
                    s.connect(InetSocketAddress(ip, 47800), 5000)
                    s.tcpNoDelay = true
                    pr = NetPeer(s)
                }
                me = pr
                pr.startWriter()
                val bo = ByteArrayOutputStream()
                val o = DataOutputStream(bo)
                o.writeByte(3)
                o.writeByte(skin)
                o.writeUTF(g.myName)
                pr.send(bo.toByteArray())
                status = "Ждём данные от хоста ..."
                while (pr.alive && running) {
                    val n = pr.din.readInt()
                    if (n < 1 || n > 300000) break
                    val b = ByteArray(n)
                    pr.din.readFully(b)
                    pr.snap = b
                    status = ""
                }
                if (running) status = "Связь потеряна. Открой меню и нажми ВЫЙТИ"
            } catch (e: Exception) {
                if (running) status = "Нет связи: " + (e.message ?: "")
            }
        }
        t.isDaemon = true
        t.start()
    }

    private fun rEnt(i: DataInputStream, e: Ent, init: Boolean, own: Boolean) {
        val x = i.readFloat()
        val y = i.readFloat()
        val ang = i.readFloat()
        val wk = i.readFloat()
        val at = i.readFloat()
        val f = i.readByte().toInt()
        e.hp = i.readShort().toFloat()
        e.maxHp = i.readShort().toFloat()
        e.dead = (f and 1) != 0
        e.atkT = at
        e.hurtT = if ((f and 4) != 0) 0.1f else 0f
        if (!own) {
            e.angle = ang
            e.walk = wk
            e.moving = (f and 2) != 0
        }
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
        val seq = i.readByte().toInt()
        val note = i.readUTF()
        if (seq != lastNote) {
            lastNote = seq
            if (note.isNotEmpty()) g.say(note)
        }
        g.applyMode(i.readByte().toInt())
        g.wave = i.readShort().toInt()
        g.zoneX = i.readFloat()
        g.zoneY = i.readFloat()
        g.zoneR = i.readFloat()
        g.zoneT = i.readFloat()
        g.pvpOver = i.readByte().toInt() != 0
        val cs = i.readByte().toInt()
        val cl = i.readUTF()
        if (lastChat == -2) lastChat = cs
        else if (cs != lastChat) { lastChat = cs; if (cl.isNotEmpty()) g.chatAdd(cl) }
        g.netPeerIn(i) // m12e
        val nh = i.readByte().toInt()
        val oldBots = ArrayList<Bot>(g.bots)
        val newBots = ArrayList<Bot>()
        val lines = ArrayList<String>()
        for (k in 0 until nh) {
            val id = i.readByte().toInt()
            val sk = i.readByte().toInt().coerceIn(0, 7) // m13sk
            if (id == myId) {
                val p = g.player
                rEnt(i, p, false, true)
                p.flash = i.readFloat()
                p.weapon = i.readByte().toInt()
                p.ammo = i.readShort().toInt()
                p.mags[p.weapon.coerceIn(0, 9)] = i.readByte().toInt()
                p.reload = i.readByte().toInt() / 20f // m13i
                p.kills = i.readShort().toInt()
                val m = i.readShort().toInt()
                i.readUTF()
                for (q in 0 until 10) p.owned[q] = (m and (1 shl q)) != 0
                lines.add(g.myName + " (ты): " + p.kills)
            } else {
                val lk = Looks.players[sk]
                var bt = takeBot(oldBots, true, lk.skin, lk.shirt, lk.pants, lk.hair, lk.hat, lk.hatColor)
                var init = false
                if (bt == null) {
                    bt = Bot(0f, 0f, lk, true)
                    init = true
                }
                rEnt(i, bt, init, false)
                bt.flash = i.readFloat()
                bt.weapon = i.readByte().toInt()
                i.readShort()
                i.readByte(); i.readByte() // m13j
                val kl = i.readShort().toInt()
                i.readShort() // m10
                bt.name = i.readUTF()
                bt.team = id % 2 // m14t
                lines.add(bt.name + ": " + kl)
                newBots.add(bt)
            }
        }
        board = lines
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
                z.r = g.zRadius(type)
                init = true
                if (k < g.zombies.size) g.zombies[k] = z else g.zombies.add(z)
            }
            rEnt(i, z, init, false)
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
            rEnt(i, z, init, false)
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
            rEnt(i, bt, init, false)
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
        val ne = i.readByte().toInt()
        for (q in 0 until ne) {
            val eid = i.readByte().toInt()
            val ex = i.readFloat()
            val ey = i.readFloat()
            g.playEvent(eid, ex, ey)
        }
        g.netWorldIn(i) // m12f
        if (!got) {
            got = true
            g.missBase = g.hostMiss
            g.player.x = g.player.wx
            g.player.y = g.player.wy
        }
    }

    private fun sendInput(pr: NetPeer, jx: Float, jy: Float, atk: Boolean, ax: Float, ay: Float) {
        val bo = ByteArrayOutputStream(32)
        val o = DataOutputStream(bo)
        o.writeByte(1)
        o.writeFloat(jx)
        o.writeFloat(jy)
        o.writeByte(if (atk) 1 else 0)
        o.writeFloat(ax)
        o.writeFloat(ay)
        o.writeByte(swSeq)
        o.writeByte(if (g.wantSprint()) 1 else 0) // m12g
        pr.send(bo.toByteArray())
    }

    fun sendCmd(c: Int) {
        val pr = me ?: return
        pr.send(byteArrayOf(5, c.toByte()))
    }

    fun sendChat(t: String) {
        val pr = me ?: return
        val bo = ByteArrayOutputStream(96)
        val o = DataOutputStream(bo)
        o.writeByte(4)
        o.writeUTF(t)
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
            val dist = hypot(p.wx - p.x, p.wy - p.y)
            var kk = 0f
            if (p.dead || dist > 150f || g.driving != null) kk = min(1f, dt * 14f) // m12h
            else if (!p.moving) kk = min(1f, dt * 4f)
            p.x += (p.wx - p.x) * kk
            p.y += (p.wy - p.y) * kk
        }
        for (l in g.loot) l.t = ctime
        for (bl in g.bullets) { bl.x += bl.vx * dt; bl.y += bl.vy * dt }
        sendT += dt
        if (sendT >= 0.04f) {
            sendT = 0f
            sendInput(pr, jx, jy, atk, ax, ay)
        }
    }
}
