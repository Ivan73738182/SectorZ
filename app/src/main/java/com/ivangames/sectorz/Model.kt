package com.ivangames.sectorz

const val PI_F = 3.1415927f

fun normAng(a: Float): Float {
    var x = a
    while (x > PI_F) x -= 2f * PI_F
    while (x < -PI_F) x += 2f * PI_F
    return x
}

fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

class Look(val skin: Int, val shirt: Int, val pants: Int, val hair: Int, val hat: Int, val hatColor: Int)

object Looks {
    val names = arrayOf("Рик", "Мия", "Макс", "Зоя", "Тим", "Ева", "Лёва", "Кира")
    val players = arrayOf(
        Look(0xFFE0AC69.toInt(), 0xFF2F6DB5.toInt(), 0xFF3B3B4A.toInt(), 0xFF4A2F1B.toInt(), 1, 0xFFC0392B.toInt()),
        Look(0xFFF1C27D.toInt(), 0xFFB5302F.toInt(), 0xFF2B2B2B.toInt(), 0xFF1B1B1B.toInt(), 2, 0xFF5B2A86.toInt()),
        Look(0xFF8D5524.toInt(), 0xFF4F6B3A.toInt(), 0xFF5A5040.toInt(), 0xFF111111.toInt(), 3, 0xFF4C5B3A.toInt()),
        Look(0xFFFFDBAC.toInt(), 0xFF8E44AD.toInt(), 0xFF2C3E50.toInt(), 0xFFE5C24A.toInt(), 4, 0xFFE74C3C.toInt()),
        Look(0xFFF1C27D.toInt(), 0xFFF9A825.toInt(), 0xFF37474F.toInt(), 0xFFB5651D.toInt(), 0, 0xFF000000.toInt()),
        Look(0xFFFFDBAC.toInt(), 0xFF00897B.toInt(), 0xFF263238.toInt(), 0xFFB7410E.toInt(), 4, 0xFFFFFFFF.toInt()),
        Look(0xFF8D5524.toInt(), 0xFFD84315.toInt(), 0xFF1A237E.toInt(), 0xFF111111.toInt(), 2, 0xFF212121.toInt()),
        Look(0xFFE0AC69.toInt(), 0xFF3949AB.toInt(), 0xFF4E342E.toInt(), 0xFF4A2F1B.toInt(), 1, 0xFF27AE60.toInt()) // m13s
    )

    fun random(r: kotlin.random.Random): Look {
        val skins = intArrayOf(0xFFFFDBAC.toInt(), 0xFFF1C27D.toInt(), 0xFFE0AC69.toInt(), 0xFFC68642.toInt(), 0xFF8D5524.toInt())
        val shirts = intArrayOf(0xFF1E88E5.toInt(), 0xFFF9A825.toInt(), 0xFF43A047.toInt(), 0xFFD84315.toInt(), 0xFF6D4C41.toInt(), 0xFF00897B.toInt(), 0xFFEEEEEE.toInt(), 0xFF3949AB.toInt())
        val pants = intArrayOf(0xFF37474F.toInt(), 0xFF263238.toInt(), 0xFF4E342E.toInt(), 0xFF1A237E.toInt())
        val hairs = intArrayOf(0xFF111111.toInt(), 0xFF4A2F1B.toInt(), 0xFFE5C24A.toInt(), 0xFFB5651D.toInt(), 0xFF9E9E9E.toInt())
        val hats = intArrayOf(0xFFC0392B.toInt(), 0xFF2E86C1.toInt(), 0xFF2C3E50.toInt(), 0xFFF39C12.toInt(), 0xFF27AE60.toInt())
        return Look(
            skins[r.nextInt(skins.size)], shirts[r.nextInt(shirts.size)], pants[r.nextInt(pants.size)],
            hairs[r.nextInt(hairs.size)], r.nextInt(5), hats[r.nextInt(hats.size)]
        )
    }
}

object W {
    val names = arrayOf("Кулаки", "Нож", "Бита", "Пистолет", "Автомат", "Винтовка", "Дробовик", "Снайперка", "Катана", "Бензопила")
    val range = floatArrayOf(34f, 40f, 54f, 0f, 0f, 0f, 0f, 0f, 62f, 50f)
    val dmg = floatArrayOf(10f, 20f, 32f, 24f, 13f, 22f, 12f, 90f, 55f, 18f)
    val cd = floatArrayOf(0.45f, 0.35f, 0.6f, 0.33f, 0.08f, 0.13f, 0.85f, 1.3f, 0.5f, 0.12f)
    val spread = floatArrayOf(0f, 0f, 0f, 0.03f, 0.10f, 0.06f, 0.16f, 0.005f, 0f, 0f)
    val gunLen = floatArrayOf(0f, 0f, 0f, 12f, 20f, 28f, 22f, 34f, 0f, 0f)
    fun isGun(id: Int): Boolean = id in 3..7
    val mag = intArrayOf(0, 0, 0, 12, 30, 20, 6, 5, 0, 0)
    val rel = floatArrayOf(0f, 0f, 0f, 1.0f, 1.6f, 1.8f, 2.2f, 2.4f, 0f, 0f)
}

class Wall(val x: Float, val y: Float, val w: Float, val h: Float)
class Prop(val x: Float, val y: Float, val w: Float, val h: Float, val kind: Int, val color: Int)
class Building(val x: Float, val y: Float, val w: Float, val h: Float, val doorX: Float, val roofColor: Int) {
    var roofA = 1f
}
class Decal(val x: Float, val y: Float, val kind: Int, val size: Float)
class Loot(val x: Float, val y: Float, val type: Int, val id: Int) {
    var t = 0f
}
class Bullet(var x: Float, var y: Float, val vx: Float, val vy: Float, val dmg: Float, var life: Float, val mine: Boolean) {
    var owner: Ent? = null
}
class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int, val size: Float)

open class Ent(var x: Float, var y: Float) {
    var hunt = false
    var angle = 0f
    var hp = 100f
    var maxHp = 100f
    var r = 14f
    var walk = 0f
    var moving = false
    var atkT = -1f
    var atkHit = false
    var hurtT = 0f
    var cool = 0f
    var dead = false
    var deadT = 0f
    var avoidT = 0f
    var avoidA = 0f
    var stuckT = 0f
    var wanderT = 0f
    var wx = 0f
    var wy = 0f
}

class Player(x: Float, y: Float, val look: Look) : Ent(x, y) {
    var weapon = 3
    val owned = BooleanArray(10)
    var ammo = 24
    var kills = 0
    var flash = 0f
    val mags = IntArray(10)
    var reload = 0f // m13p
}

class Bot(x: Float, y: Float, val look: Look, val ally: Boolean) : Ent(x, y) {
    var peer: NetPeer? = null
    var name = ""
    val owned = BooleanArray(10)
    var ammo = 24
    var kills = 0
    var note = ""
    var noteSeq = 0
    fun say(s: String) { note = s; noteSeq = (noteSeq + 1) and 127 }
    var weapon = 3
    var flash = 0f
    var gren = 2
    var molo = 1
    var traps = 2
    var barrs = 2
    var stam = 100f
    var tired = false
    var sprint = false
    var throwCd = 0f // m12
    var team = 0
    var car: GameView.Car? = null
    val mags = IntArray(10)
    var reload = 0f // m13b
}

class Zombie(x: Float, y: Float, val type: Int, val variant: Int) : Ent(x, y) { var aux = 0f }
class Dog(x: Float, y: Float) : Ent(x, y)
