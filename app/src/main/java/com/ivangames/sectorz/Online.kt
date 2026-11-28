package com.ivangames.sectorz

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

// Виртуальное "соединение" поверх WebSocket: игра читает и пишет как из обычного сокета
class Pipe(private val sender: (ByteArray) -> Unit) {
    private val q = LinkedBlockingQueue<ByteArray>()
    private val end = ByteArray(0)
    @Volatile private var closed = false

    fun push(b: ByteArray) {
        if (!closed && b.isNotEmpty()) q.offer(b)
    }

    fun closeAll() {
        if (!closed) {
            closed = true
            q.offer(end)
        }
    }

    val ins: InputStream = object : InputStream() {
        private var cur: ByteArray? = null
        private var pos = 0
        private var eof = false

        override fun read(): Int {
            val a = ByteArray(1)
            val n = read(a, 0, 1)
            return if (n <= 0) -1 else a[0].toInt() and 255
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (eof) return -1
            var c = cur
            if (c == null || pos >= c.size) {
                try {
                    c = q.take()
                } catch (e: InterruptedException) {
                    return -1
                }
                if (c === end) {
                    eof = true
                    return -1
                }
                cur = c
                pos = 0
            }
            val n = minOf(len, c.size - pos)
            System.arraycopy(c, pos, b, off, n)
            pos += n
            return n
        }
    }

    val outs: OutputStream = object : OutputStream() {
        private val buf = ByteArrayOutputStream()
        override fun write(b: Int) { buf.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { buf.write(b, off, len) }
        override fun flush() {
            if (buf.size() > 0) {
                val x = buf.toByteArray()
                buf.reset()
                if (!closed) sender(x)
            }
        }
    }
}

object Online {
    // Адрес твоего сервера (после запуска на Render замени командой из инструкции)
    const val URL = "wss://sectorz-server.onrender.com"

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile var ws: WebSocket? = null
    @Volatile var status = ""
    @Volatile var count = 0
    @Volatile var left = 20
    @Volatile var started = false
    @Volatile var inGame = false
    @Volatile var host = false
    @Volatile var n = 0
    @Volatile var mode = 0
    @Volatile var myId = 0
    @Volatile var onLeft: ((Int) -> Unit)? = null
    private val pipes = arrayOfNulls<Pipe>(4)
    @Volatile private var guestPipe: Pipe? = null

    fun configured(): Boolean = !URL.contains("YOUR-SERVER")

    // будим сервер заранее (бесплатный хостинг засыпает)
    fun wake() {
        if (!configured()) return
        val t = Thread {
            try {
                client.newCall(Request.Builder().url(URL.replace("wss://", "https://")).build()).execute().close()
            } catch (e: Exception) { }
        }
        t.isDaemon = true
        t.start()
    }

    fun find() {
        cancel()
        count = 0
        left = 20
        started = false
        if (!configured()) {
            status = "Сервер не настроен в игре"
            return
        }
        status = "Подключаемся к серверу..."
        val req = Request.Builder().url(URL).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== ws) return
                status = "Ищем игроков..."
                webSocket.send("{\"t\":\"find\"}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== ws) return
                try {
                    val o = JSONObject(text)
                    val t = o.getString("t")
                    if (t == "q") {
                        count = o.getInt("n")
                        left = o.getInt("left")
                    } else if (t == "start") {
                        host = o.getString("role") == "host"
                        myId = o.getInt("id")
                        n = o.getInt("n")
                        mode = o.getInt("mode")
                        if (host) {
                            for (i in 1 until n) {
                                val tag = i.toByte()
                                pipes[i] = Pipe { data ->
                                    val m = ByteArray(data.size + 1)
                                    m[0] = tag
                                    System.arraycopy(data, 0, m, 1, data.size)
                                    webSocket.send(m.toByteString())
                                }
                            }
                        } else {
                            guestPipe = Pipe { data -> webSocket.send(data.toByteString()) }
                        }
                        inGame = true
                        started = true
                    } else if (t == "left") {
                        val id = o.getInt("id")
                        pipes[id]?.closeAll()
                        onLeft?.invoke(id)
                    } else if (t == "end") {
                        status = "Хост вышел из игры"
                        guestPipe?.closeAll()
                    }
                } catch (e: Exception) { }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (webSocket !== ws) return
                val b = bytes.toByteArray()
                if (host) {
                    if (b.size < 2) return
                    val i = b[0].toInt()
                    if (i in 1..3) pipes[i]?.push(b.copyOfRange(1, b.size))
                } else {
                    guestPipe?.push(b)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                lost(webSocket, "Соединение закрыто")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                lost(webSocket, "Нет связи с сервером")
            }
        })
    }

    private fun lost(w: WebSocket, msg: String) {
        if (w !== ws) return
        status = msg
        for (p in pipes) p?.closeAll()
        guestPipe?.closeAll()
    }

    fun hostPipe(i: Int): Pipe = pipes[i] ?: Pipe { }

    fun guestPeer(): NetPeer {
        val p = guestPipe ?: Pipe { }
        return NetPeer(p.ins, p.outs) { p.closeAll() }
    }

    fun cancel() {
        val w = ws
        ws = null
        try { w?.close(1000, "bye") } catch (e: Exception) { }
        for (i in 0 until 4) { pipes[i]?.closeAll(); pipes[i] = null }
        guestPipe?.closeAll()
        guestPipe = null
        started = false
        inGame = false
        onLeft = null
    }

    fun leave() = cancel()
}
