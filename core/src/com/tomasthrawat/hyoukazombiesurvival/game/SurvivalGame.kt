package com.tomasthrawat.hyoukazombiesurvival.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import kotlin.math.sqrt

class SurvivalGame : ApplicationAdapter() {
    private enum class State { MENU, PLAYING, PAUSED, GAME_OVER }
    private enum class PickupType { HEALTH, ENERGY, XP }

    private lateinit var camera: OrthographicCamera
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont

    private val player = Player()
    private val zombies = ArrayList<Zombie>(40)
    private val pickups = ArrayList<Pickup>(20)
    private val effects = ArrayList<Effect>(20)

    private var state = State.MENU
    private var wave = 1
    private var score = 0
    private var kills = 0
    private var level = 1
    private var xp = 0f
    private var xpNext = 100f
    private var waveTime = 0f
    private var spawnTimer = 0f
    private var flash = 0f
    private var joystickId = -1
    private var joystickX = 0f
    private var joystickY = 0f

    private val uiMatrix = com.badlogic.gdx.math.Matrix4()
    private val worldW = 100f
    private val worldH = 60f

    override fun create() {
        camera = OrthographicCamera(worldW, worldH)
        camera.position.set(worldW / 2f, worldH / 2f, 0f)
        camera.update()

        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
        font.data.setScale(1.1f)

        Gdx.input.inputProcessor = GameInput()
        reset()
    }

    private fun reset() {
        player.reset()
        zombies.clear()
        pickups.clear()
        effects.clear()
        wave = 1
        score = 0
        kills = 0
        level = 1
        xp = 0f
        xpNext = 100f
        waveTime = 0f
        spawnTimer = 0f
        flash = 0f
        joystickId = -1
        joystickX = 0f
        joystickY = 0f
    }

    private fun startGame() {
        reset()
        state = State.PLAYING
        repeat(4) { spawnZombie() }
    }

    override fun render() {
        val dt = MathUtils.clamp(Gdx.graphics.deltaTime, 0f, 0.033f)
        if (state == State.PLAYING) update(dt)
        draw()
    }

    private fun update(dt: Float) {
        waveTime += dt
        spawnTimer += dt
        flash = maxOf(0f, flash - dt)

        updatePlayer(dt)
        updateZombies(dt)
        updatePickups(dt)
        updateEffects(dt)

        val target = minOf(8 + wave * 2, 34)
        val interval = maxOf(0.55f, 2.0f - wave * 0.07f)
        if (spawnTimer >= interval && zombies.size < target) {
            spawnTimer = 0f
            spawnZombie()
        }

        if (waveTime >= 30f) {
            wave++
            waveTime = 0f
            effects += Effect(player.x, player.y, 8f, 0.55f)
            if (wave % 3 == 0) pickups += Pickup(randomX(), randomY(), PickupType.ENERGY)
        }

        if (player.health <= 0f) {
            player.health = 0f
            state = State.GAME_OVER
            saveProgress()
        }
    }

    private fun updatePlayer(dt: Float) {
        var mx = joystickX
        var my = joystickY
        if (Gdx.input.isKeyPressed(Input.Keys.W)) my += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.S)) my -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.A)) mx -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.D)) mx += 1f

        val len = sqrt(mx * mx + my * my)
        if (len > 1f) {
            mx /= len
            my /= len
        }

        player.x = MathUtils.clamp(player.x + mx * player.speed * dt, 2.5f, worldW - 2.5f)
        player.y = MathUtils.clamp(player.y + my * player.speed * dt, 2.5f, worldH - 2.5f)
        player.energy = minOf(100f, player.energy + (7f + level * 0.2f) * dt)
        player.health = minOf(player.maxHealth, player.health + 0.5f * dt)
        player.pulseCooldown = maxOf(0f, player.pulseCooldown - dt)
        player.novaCooldown = maxOf(0f, player.novaCooldown - dt)
    }

    private fun updateZombies(dt: Float) {
        val it = zombies.iterator()
        while (it.hasNext()) {
            val z = it.next()
            if (z.dead) {
                it.remove()
                continue
            }

            val dx = player.x - z.x
            val dy = player.y - z.y
            val d2 = dx * dx + dy * dy
            if (d2 > 2.25f) {
                val inv = 1f / sqrt(maxOf(d2, 0.0001f))
                z.x += dx * inv * z.speed * dt
                z.y += dy * inv * z.speed * dt
            } else if (z.attackTimer <= 0f) {
                player.health -= z.damage
                z.attackTimer = 0.75f
                flash = 0.12f
            }
            z.attackTimer = maxOf(0f, z.attackTimer - dt)
        }
    }

    private fun updatePickups(dt: Float) {
        val it = pickups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            val dx = p.x - player.x
            val dy = p.y - player.y
            if (dx * dx + dy * dy < 2.6f) {
                when (p.type) {
                    PickupType.HEALTH -> player.health = minOf(player.maxHealth, player.health + 28f)
                    PickupType.ENERGY -> player.energy = minOf(100f, player.energy + 35f)
                    PickupType.XP -> addXp(45f)
                }
                effects += Effect(p.x, p.y, 2.2f, 0.35f)
                it.remove()
            }
        }
    }

    private fun updateEffects(dt: Float) {
        val it = effects.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.age += dt
            if (e.age >= e.life) it.remove()
        }
    }

    private fun pulse() {
        if (state != State.PLAYING || player.pulseCooldown > 0f || player.energy < 25f) return
        player.energy -= 25f
        player.pulseCooldown = 0.45f
        val radius = 6.5f + level * 0.08f
        effects += Effect(player.x, player.y, radius, 0.45f)
        damageRadius(radius, 2.6f + level * 0.4f, true)
    }

    private fun nova() {
        if (state != State.PLAYING || player.novaCooldown > 0f || player.energy < 50f) return
        player.energy -= 50f
        player.novaCooldown = 4.5f
        val radius = 11f + level * 0.12f
        effects += Effect(player.x, player.y, radius, 0.65f)
        damageRadius(radius, 5.8f + level * 0.65f, false)
    }

    private fun damageRadius(radius: Float, damage: Float, knockback: Boolean) {
        val r2 = radius * radius
        for (z in zombies) {
            if (z.dead) continue
            val dx = z.x - player.x
            val dy = z.y - player.y
            val d2 = dx * dx + dy * dy
            if (d2 <= r2) {
                z.health -= damage
                if (knockback && d2 > 0.01f) {
                    val inv = 1f / sqrt(d2)
                    z.x = MathUtils.clamp(z.x + dx * inv * 2.5f, 2f, worldW - 2f)
                    z.y = MathUtils.clamp(z.y + dy * inv * 2.5f, 2f, worldH - 2f)
                }
                if (z.health <= 0f) killZombie(z)
            }
        }
    }

    private fun killZombie(z: Zombie) {
        if (z.dead) return
        z.dead = true
        kills++
        score += 10 + wave * 2
        addXp(22f + wave * 2f)
        when (MathUtils.random(0, 7)) {
            0 -> pickups += Pickup(z.x, z.y, PickupType.HEALTH)
            1 -> pickups += Pickup(z.x, z.y, PickupType.ENERGY)
            2 -> pickups += Pickup(z.x, z.y, PickupType.XP)
        }
    }

    private fun addXp(amount: Float) {
        xp += amount
        while (xp >= xpNext) {
            xp -= xpNext
            level++
            xpNext = 100f + level * 35f
            player.maxHealth += 6f
            player.health = player.maxHealth
            player.energy = 100f
            effects += Effect(player.x, player.y, 5f, 0.55f)
        }
    }

    private fun spawnZombie() {
        val edge = MathUtils.random(0, 3)
        val x: Float
        val y: Float
        when (edge) {
            0 -> { x = 2f; y = randomY() }
            1 -> { x = worldW - 2f; y = randomY() }
            2 -> { x = randomX(); y = 2f }
            else -> { x = randomX(); y = worldH - 2f }
        }
        zombies += Zombie(
            x,
            y,
            2.6f + wave * 0.45f,
            1.05f + wave * 0.045f + MathUtils.random(0f, 0.3f),
            4.5f + wave * 0.35f
        )
    }

    private fun randomX() = MathUtils.random(5f, worldW - 5f)
    private fun randomY() = MathUtils.random(5f, worldH - 5f)

    private fun draw() {
        Gdx.gl.glClearColor(0.025f, 0.04f, 0.07f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.position.set(player.x, player.y, 0f)
        camera.update()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        shapes.color = Color(0.055f, 0.01f, 0.075f, 1f)
        shapes.rect(0f, 0f, worldW, worldH)

        shapes.color = Color(0.09f, 0.16f, 0.11f, 1f)
        for (x in 0..100 step 5) shapes.rect(x.toFloat(), 0f, 0.05f, worldH)
        for (y in 0..60 step 5) shapes.rect(0f, y.toFloat(), worldW, 0.05f)

        for (p in pickups) {
            shapes.color = when (p.type) {
                PickupType.HEALTH -> Color(0.25f, 0.9f, 0.35f, 1f)
                PickupType.ENERGY -> Color(0.25f, 0.75f, 1f, 1f)
                PickupType.XP -> Color(1f, 0.75f, 0.2f, 1f)
            }
            shapes.circle(p.x, p.y, 0.62f + kotlin.math.sin(p.age * 5f) * 0.12f)
        }

        for (z in zombies) {
            if (z.dead) continue
            shapes.color = Color(0f, 0f, 0f, 0.25f)
            shapes.ellipse(z.x - 0.9f, z.y - 0.35f, 1.8f, 0.7f)
            shapes.color = Color(0.36f, 0.64f, 0.28f, 1f)
            shapes.circle(z.x, z.y + 0.25f, 0.85f)
            shapes.color = Color(0.46f, 0.72f, 0.34f, 1f)
            shapes.circle(z.x, z.y + 1f, 0.52f)
            shapes.color = Color(0.06f, 0.10f, 0.05f, 1f)
            shapes.circle(z.x - 0.18f, z.y + 1.08f, 0.08f)
            shapes.circle(z.x + 0.18f, z.y + 1.08f, 0.08f)
        }

        for (e in effects) {
            val p = e.age / e.life
            val alpha = 0.55f * (1f - p)
            shapes.color = Color(0.25f, 0.75f, 1f, alpha)
            shapes.circle(e.x, e.y, e.radius * p)
        }

        shapes.color = Color(0f, 0f, 0f, 0.25f)
        shapes.ellipse(player.x - 1.05f, player.y - 0.4f, 2.1f, 0.8f)
        shapes.color = Color(0.25f, 0.65f, 1f, 1f)
        shapes.circle(player.x, player.y + 0.35f, 0.95f)
        shapes.color = Color(0.75f, 0.92f, 1f, 1f)
        shapes.circle(player.x, player.y + 1f, 0.58f)
        shapes.end()

        drawHud()
    }

    private fun drawHud() {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        uiMatrix.setToOrtho2D(0f, 0f, w, h)

        shapes.projectionMatrix = uiMatrix
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.58f)
        shapes.rect(0f, h - 96f, w, 96f)
        drawBar(24f, h - 34f, 250f, 12f, player.health / player.maxHealth, Color(0.9f, 0.18f, 0.18f, 1f))
        drawBar(24f, h - 56f, 250f, 10f, player.energy / 100f, Color(0.2f, 0.65f, 1f, 1f))
        drawBar(24f, h - 78f, 250f, 8f, xp / xpNext, Color(1f, 0.75f, 0.2f, 1f))

        if (state == State.PLAYING) {
            shapes.color = Color(0.1f, 0.12f, 0.16f, 0.75f)
            shapes.circle(105f, 105f, 58f)
            shapes.color = Color(0.8f, 0.9f, 1f, 0.28f)
            shapes.circle(105f + joystickX * 38f, 105f + joystickY * 38f, 22f)
            shapes.color = if (player.pulseCooldown <= 0f && player.energy >= 25f) Color(0.2f, 0.75f, 1f, 0.65f) else Color(0.2f, 0.3f, 0.4f, 0.55f)
            shapes.circle(w - 105f, 105f, 55f)
            shapes.color = if (player.novaCooldown <= 0f && player.energy >= 50f) Color(0.7f, 0.35f, 1f, 0.65f) else Color(0.35f, 0.25f, 0.45f, 0.55f)
            shapes.circle(w - 190f, 105f, 42f)
        }

        if (state != State.PLAYING) {
            shapes.color = Color(0f, 0f, 0f, 0.72f)
            shapes.rect(0f, 0f, w, h)
        }
        shapes.end()

        batch.projectionMatrix = uiMatrix
        batch.begin()
        font.color = Color.WHITE
        font.data.setScale(1f)
        font.draw(batch, "WAVE " + wave + "    SCORE " + score + "    LEVEL " + level, 24f, h - 10f)
        font.draw(batch, "HP " + player.health.toInt() + "    ENERGY " + player.energy.toInt() + "    XP " + xp.toInt() + "/" + xpNext.toInt(), 292f, h - 14f)

        when (state) {
            State.MENU -> {
                font.data.setScale(2.3f)
                font.draw(batch, "HYOUKA: SURVIVAL", w / 2f - 190f, h * 0.66f)
                font.data.setScale(1.3f)
                font.draw(batch, "ENERGY SURVIVAL", w / 2f - 105f, h * 0.58f)
                font.data.setScale(1.1f)
                font.draw(batch, "TAP TO START", w / 2f - 72f, h * 0.45f)
                font.data.setScale(0.85f)
                font.draw(batch, "Move: left joystick    Pulse: blue    Nova: purple", w / 2f - 170f, h * 0.38f)
            }
            State.PLAYING -> {
                font.data.setScale(0.95f)
                font.draw(batch, "PULSE", w - 130f, 98f)
                font.draw(batch, "NOVA", w - 214f, 98f)
            }
            State.PAUSED -> {
                font.data.setScale(2.2f)
                font.draw(batch, "PAUSED", w / 2f - 68f, h * 0.58f)
                font.data.setScale(1.1f)
                font.draw(batch, "TAP TO RESUME", w / 2f - 85f, h * 0.48f)
            }
            State.GAME_OVER -> {
                font.data.setScale(2.2f)
                font.draw(batch, "RUN OVER", w / 2f - 82f, h * 0.62f)
                font.data.setScale(1.15f)
                font.draw(batch, "WAVE " + wave + "   SCORE " + score + "   KILLS " + kills, w / 2f - 145f, h * 0.52f)
                font.draw(batch, "TAP TO PLAY AGAIN", w / 2f - 105f, h * 0.43f)
            }
        }
        batch.end()
        font.data.setScale(1.1f)
    }

    private fun drawBar(x: Float, y: Float, width: Float, height: Float, value: Float, color: Color) {
        shapes.color = Color(0.12f, 0.14f, 0.18f, 1f)
        shapes.rect(x, y, width, height)
        shapes.color = color
        shapes.rect(x, y, width * MathUtils.clamp(value, 0f, 1f), height)
    }

    private fun saveProgress() {
        Gdx.app.getPreferences("hyouka_survival").apply {
            putInteger("bestScore", maxOf(getInteger("bestScore", 0), score))
            putInteger("bestWave", maxOf(getInteger("bestWave", 1), wave))
            putInteger("totalKills", getInteger("totalKills", 0) + kills)
            flush()
        }
    }

    override fun pause() {
        if (state == State.PLAYING) state = State.PAUSED
        saveProgress()
    }

    override fun resume() {}

    override fun dispose() {
        saveProgress()
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }

    private inner class GameInput : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val w = Gdx.graphics.width.toFloat()
            val h = Gdx.graphics.height.toFloat()
            val y = h - screenY

            if (state == State.MENU || state == State.GAME_OVER) {
                startGame()
                return true
            }
            if (state == State.PAUSED) {
                state = State.PLAYING
                return true
            }

            if (screenX < w * 0.48f && y < h * 0.52f) {
                joystickId = pointer
                updateJoystick(screenX, y.toInt())
                return true
            }
            if (screenX > w * 0.82f && y < 180f) {
                pulse()
                return true
            }
            if (screenX > w * 0.68f && screenX <= w * 0.82f && y < 180f) {
                nova()
                return true
            }
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (pointer == joystickId) {
                updateJoystick(screenX, Gdx.graphics.height - screenY)
                return true
            }
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer == joystickId) {
                joystickId = -1
                joystickX = 0f
                joystickY = 0f
            }
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.ESCAPE) {
                state = when (state) {
                    State.PLAYING -> State.PAUSED
                    State.PAUSED -> State.PLAYING
                    else -> state
                }
                return true
            }
            if (keycode == Input.Keys.SPACE) {
                pulse()
                return true
            }
            if (keycode == Input.Keys.N) {
                nova()
                return true
            }
            return false
        }

        private fun updateJoystick(x: Int, y: Int) {
            joystickX = MathUtils.clamp((x - 105f) / 50f, -1f, 1f)
            joystickY = MathUtils.clamp((y - 105f) / 50f, -1f, 1f)
        }
    }

    private class Player {
        var x = 50f
        var y = 30f
        var health = 100f
        var maxHealth = 100f
        var energy = 100f
        var speed = 8.5f
        var pulseCooldown = 0f
        var novaCooldown = 0f

        fun reset() {
            x = 50f
            y = 30f
            health = 100f
            maxHealth = 100f
            energy = 100f
            speed = 8.5f
            pulseCooldown = 0f
            novaCooldown = 0f
        }
    }

    private class Zombie(
        var x: Float,
        var y: Float,
        var health: Float,
        val speed: Float,
        val damage: Float
    ) {
        var attackTimer = 0f
        var dead = false
    }

    private class Pickup(val x: Float, val y: Float, val type: PickupType) {
        var age = 0f
    }

    private class Effect(
        val x: Float,
        val y: Float,
        val radius: Float,
        val life: Float
    ) {
        var age = 0f
    }
}
