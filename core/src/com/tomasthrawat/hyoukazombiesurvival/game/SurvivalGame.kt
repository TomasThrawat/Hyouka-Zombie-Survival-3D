
package com.tomasthrawat.hyoukazombiesurvival.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class SurvivalGame : ApplicationAdapter() {
    private enum class State { MENU, PLAYING, PAUSED, GAME_OVER }
    private enum class PickupType { HEALTH, ENERGY, XP }
    private enum class EffectType { PULSE, NOVA, WAVE, PICKUP }

    private lateinit var batch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var camera: PerspectiveCamera
    private lateinit var ui: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var shapes: ShapeRenderer
    private lateinit var assets: GameAssets

    private val player = Player()
    private val zombies = ArrayList<Zombie>(48)
    private val pickups = ArrayList<Pickup>(16)
    private val effects = ArrayList<Effect>(24)

    private var state = State.MENU
    private var wave = 1
    private var score = 0
    private var kills = 0
    private var level = 1
    private var xp = 0f
    private var xpToNext = 100f
    private var waveTime = 0f
    private var spawnTimer = 0f
    private var elapsed = 0f
    private var flash = 0f
    private var saveTimer = 0f
    private var joystickPointer = -1
    private var lookPointer = -1
    private var abilityPointer = -1
    private var joyX = 0f
    private var joyY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun create() {
        batch = ModelBatch(DefaultShaderProvider(DefaultShader.Config().apply {
            numDirectionalLights = 1
            numPointLights = 0
            numSpotLights = 0
            numBones = 0
        }))
        environment = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.72f, 0.78f, 0.72f, 1f))
            add(DirectionalLight().set(0.85f, 0.9f, 0.82f, -0.55f, -1f, -0.35f))
        }
        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            near = 0.15f
            far = 110f
        }
        ui = SpriteBatch()
        font = BitmapFont()
        font.data.setScale(1.15f)
        shapes = ShapeRenderer()
        assets = GameAssets()
        Gdx.input.inputProcessor = GameInput()
        resetRun()
    }

    private fun resetRun() {
        player.reset()
        zombies.clear()
        pickups.clear()
        effects.clear()
        wave = 1
        score = 0
        kills = 0
        level = 1
        xp = 0f
        xpToNext = 100f
        waveTime = 0f
        spawnTimer = 0f
        elapsed = 0f
        flash = 0f
        saveTimer = 0f
        joystickPointer = -1
        lookPointer = -1
        abilityPointer = -1
        joyX = 0f
        joyY = 0f
    }

    private fun startGame() {
        resetRun()
        state = State.PLAYING
        repeat(3) { spawnZombie() }
    }

    override fun render() {
        val dt = MathUtils.clamp(Gdx.graphics.deltaTime, 0f, 0.033f)
        if (state == State.PLAYING) update(dt)
        drawWorld()
        drawHud()
    }

    private fun update(dt: Float) {
        elapsed += dt
        waveTime += dt
        spawnTimer += dt
        flash = maxOf(0f, flash - dt)
        saveTimer += dt

        updatePlayer(dt)
        updateZombies(dt)
        updatePickups(dt)
        updateEffects(dt)

        val targetCount = minOf(8 + wave * 2, 34)
        val interval = maxOf(0.55f, 2.25f - wave * 0.075f)
        if (spawnTimer >= interval && zombies.size < targetCount) {
            spawnTimer = 0f
            spawnZombie()
        }

        if (waveTime >= 30f) {
            wave++
            waveTime = 0f
            spawnTimer = 0f
            effects += Effect(player.pos.cpy(), 0f, 4.5f, EffectType.WAVE)
            if (wave % 3 == 0) pickups += Pickup(randomPoint(), PickupType.ENERGY)
        }

        if (player.health <= 0f) {
            player.health = 0f
            state = State.GAME_OVER
        }

        if (saveTimer >= 5f) {
            saveTimer = 0f
            saveProgress()
        }
    }

    private fun updatePlayer(dt: Float) {
        val forward = Vector3(-sin(player.yaw), 0f, -cos(player.yaw))
        val right = Vector3(cos(player.yaw), 0f, -sin(player.yaw))
        val move = Vector3(forward).scl(joyY).mulAdd(right, joyX)
        if (move.len2() > 0.001f) {
            move.nor()
            player.pos.mulAdd(move, player.speed * dt)
        }
        player.pos.x = MathUtils.clamp(player.pos.x, -24f, 24f)
        player.pos.z = MathUtils.clamp(player.pos.z, -24f, 24f)
        player.energy = minOf(100f, player.energy + (7f + level * 0.25f) * dt)
        player.health = minOf(player.maxHealth, player.health + 0.9f * dt)
        player.pulseCooldown = maxOf(0f, player.pulseCooldown - dt)
        player.novaCooldown = maxOf(0f, player.novaCooldown - dt)

        camera.position.set(
            player.pos.x - sin(player.yaw) * 6.4f,
            player.pos.y + 4.7f,
            player.pos.z - cos(player.yaw) * 6.4f
        )
        camera.lookAt(player.pos.x, player.pos.y + 0.75f, player.pos.z)
        camera.up.set(Vector3.Y)
        camera.update()
    }

    private fun updateZombies(dt: Float) {
        for (z in zombies) {
            if (z.dead) continue
            if (z.health <= 0f) {
                killZombie(z)
                continue
            }
            val dx = player.pos.x - z.pos.x
            val dz = player.pos.z - z.pos.z
            val d2 = dx * dx + dz * dz
            if (d2 > 2.1f) {
                val inv = 1f / sqrt(maxOf(d2, 0.0001f))
                z.pos.x += dx * inv * z.speed * dt
                z.pos.z += dz * inv * z.speed * dt
            } else if (z.attackTimer <= 0f) {
                player.health -= z.damage
                z.attackTimer = 0.75f
                flash = 0.18f
            }
            z.attackTimer = maxOf(0f, z.attackTimer - dt)
        }
        zombies.removeAll { it.dead }
    }

    private fun updatePickups(dt: Float) {
        val it = pickups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.pos.dst2(player.pos) < 2.0f) {
                when (p.type) {
                    PickupType.ENERGY -> player.energy = minOf(100f, player.energy + 35f)
                    PickupType.HEALTH -> player.health = minOf(player.maxHealth, player.health + 28f)
                    PickupType.XP -> addXp(45f)
                }
                effects += Effect(p.pos.cpy(), 0f, 0.35f, EffectType.PICKUP)
                it.remove()
            }
        }
    }

    private fun updateEffects(dt: Float) {
        val it = effects.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.age += dt
            e.radius = when (e.type) {
                EffectType.PULSE -> minOf(e.maxRadius, e.radius + 16f * dt)
                EffectType.NOVA -> minOf(e.maxRadius, e.radius + 22f * dt)
                EffectType.WAVE -> minOf(e.maxRadius, e.radius + 12f * dt)
                EffectType.PICKUP -> e.radius + 2f * dt
            }
            if (e.age >= maxOf(e.life, 0.35f)) it.remove()
        }
    }

    private fun pulse() {
        if (state != State.PLAYING || player.pulseCooldown > 0f || player.energy < 25f) return
        player.energy -= 25f
        player.pulseCooldown = 0.45f
        val center = player.pos.cpy().mulAdd(Vector3(-sin(player.yaw), 0f, -cos(player.yaw)), 1.25f)
        val radius = 5.8f + level * 0.08f
        effects += Effect(center, 0.2f, radius, EffectType.PULSE)
        for (z in zombies) {
            if (!z.dead && z.pos.dst2(center) <= radius * radius) {
                z.health -= 2.4f + level * 0.45f
                val push = Vector3(z.pos).sub(center)
                push.y = 0f
                if (push.len2() > 0.01f) z.pos.mulAdd(push.nor(), 1.0f)
            }
        }
    }

    private fun nova() {
        if (state != State.PLAYING || player.novaCooldown > 0f || player.energy < 50f) return
        player.energy -= 50f
        player.novaCooldown = 4.5f
        val radius = 9.5f + level * 0.12f
        effects += Effect(player.pos.cpy(), 0.2f, radius, EffectType.NOVA)
        for (z in zombies) {
            if (!z.dead && z.pos.dst2(player.pos) <= radius * radius) {
                z.health -= 5.5f + level * 0.65f
            }
        }
    }

    private fun spawnZombie() {
        val angle = MathUtils.random(0f, MathUtils.PI2)
        val radius = MathUtils.random(15f, 22.5f)
        val p = Vector3(cos(angle) * radius, 0.9f, sin(angle) * radius)
        val hp = 2.4f + wave * 0.45f
        val speed = 1.05f + wave * 0.045f + MathUtils.random(0f, 0.35f)
        zombies += Zombie(p, hp, speed, 5.5f + wave * 0.25f)
    }

    private fun randomPoint() = Vector3(MathUtils.random(-20f, 20f), 0.5f, MathUtils.random(-20f, 20f))

    private fun addXp(amount: Float) {
        xp += amount
        while (xp >= xpToNext) {
            xp -= xpToNext
            level++
            xpToNext = 100f + level * 35f
            player.maxHealth += 6f
            player.health = player.maxHealth
            player.energy = 100f
            effects += Effect(player.pos.cpy(), 0f, 4.5f, EffectType.NOVA)
        }
    }

    private fun killZombie(z: Zombie) {
        if (z.dead) return
        z.dead = true
        kills++
        score += 10 + wave * 2
        addXp(22f + wave * 2f)
        when (MathUtils.random(0, 9)) {
            0 -> pickups += Pickup(z.pos.cpy().apply { y = 0.5f }, PickupType.HEALTH)
            1 -> pickups += Pickup(z.pos.cpy().apply { y = 0.5f }, PickupType.ENERGY)
            2 -> pickups += Pickup(z.pos.cpy().apply { y = 0.5f }, PickupType.XP)
        }
    }

    private fun drawWorld() {
        Gdx.gl.glClearColor(0.025f, 0.045f, 0.07f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        batch.begin(camera)
        batch.render(assets.ground, environment)
        for (p in assets.props) batch.render(p, environment)

        assets.player.transform.setToTranslation(player.pos.x, player.pos.y, player.pos.z)
        batch.render(assets.player, environment)

        for (z in zombies) {
            assets.zombie.transform.setToTranslation(z.pos.x, z.pos.y, z.pos.z)
            batch.render(assets.zombie, environment)
            assets.zombieHead.transform.setToTranslation(z.pos.x, z.pos.y + 1.05f, z.pos.z)
            batch.render(assets.zombieHead, environment)
        }

        for (p in pickups) {
            val bob = 0.55f + sin(elapsed * 3f + p.age * 2f) * 0.12f
            assets.pickup.transform.setToTranslation(p.pos.x, bob, p.pos.z)
            batch.render(assets.pickup, environment)
        }

        for (e in effects) {
            assets.effect.transform.setToTranslation(e.pos.x, 0.12f, e.pos.z)
            assets.effect.transform.setToScaling(e.radius, 0.08f, e.radius)
            batch.render(assets.effect, environment)
        }
        batch.end()
    }

    private fun drawHud() {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.34f)
        shapes.rect(0f, h - 82f, w, 82f)
        shapes.color = Color(0.05f, 0.8f, 0.4f, 0.85f)
        shapes.rect(24f, h - 36f, 250f * (player.health / player.maxHealth), 12f)
        shapes.color = Color(0.1f, 0.55f, 1f, 0.85f)
        shapes.rect(24f, h - 58f, 250f * (player.energy / 100f), 10f)
        shapes.color = Color(0.95f, 0.7f, 0.15f, 0.85f)
        shapes.rect(24f, h - 74f, 250f * (xp / xpToNext), 7f)

        if (state != State.PLAYING) {
            shapes.color = Color(0f, 0f, 0f, 0.66f)
            shapes.rect(0f, 0f, w, h)
        } else {
            shapes.color = Color(1f, 1f, 1f, 0.10f)
            shapes.circle(105f, 105f, 64f)
            shapes.color = Color(1f, 1f, 1f, 0.28f)
            shapes.circle(105f + joyX * 38f, 105f + joyY * 38f, 22f)
            shapes.color = Color(0.15f, 0.7f, 1f, 0.16f)
            shapes.circle(w - 92f, 105f, 62f)
            shapes.color = if (player.pulseCooldown <= 0f && player.energy >= 25f) Color(0.2f, 0.85f, 1f, 0.5f) else Color(0.35f, 0.35f, 0.45f, 0.35f)
            shapes.circle(w - 92f, 105f, 48f)
            shapes.color = if (player.novaCooldown <= 0f && player.energy >= 50f) Color(0.75f, 0.35f, 1f, 0.42f) else Color(0.35f, 0.35f, 0.45f, 0.25f)
            shapes.circle(w - 180f, 105f, 38f)
        }
        shapes.end()

        ui.begin()
        when (state) {
            State.MENU -> {
                font.data.setScale(2.4f)
                font.draw(ui, "HYOUKA: SURVIVAL", w / 2f - 170f, h * 0.68f)
                font.data.setScale(1.2f)
                font.draw(ui, "3D ENERGY SURVIVAL", w / 2f - 112f, h * 0.61f)
                font.data.setScale(1.5f)
                font.draw(ui, "TAP TO START", w / 2f - 80f, h * 0.45f)
                font.data.setScale(0.95f)
                font.draw(ui, "Move: left joystick   Look: drag right side", w / 2f - 150f, h * 0.36f)
                font.draw(ui, "Pulse: blue circle   Nova: purple circle", w / 2f - 145f, h * 0.31f)
            }
            State.PLAYING -> {
                font.data.setScale(1.0f)
                font.draw(ui, "WAVE " + wave + "   SCORE " + score + "   LV " + level, 24f, h - 14f)
                font.draw(ui, "HP " + player.health.toInt() + "   ENERGY " + player.energy.toInt() + "   XP " + xp.toInt() + "/" + xpToNext.toInt(), 292f, h - 14f)
                font.draw(ui, "PULSE", w - 120f, 88f)
                font.draw(ui, "NOVA", w - 207f, 88f)
                if (flash > 0f) font.draw(ui, "HIT!", w / 2f - 18f, h * 0.58f)
            }
            State.PAUSED -> {
                font.data.setScale(2.2f)
                font.draw(ui, "PAUSED", w / 2f - 72f, h * 0.6f)
                font.data.setScale(1.2f)
                font.draw(ui, "TAP TO RESUME", w / 2f - 85f, h * 0.5f)
            }
            State.GAME_OVER -> {
                font.data.setScale(2.2f)
                font.draw(ui, "RUN OVER", w / 2f - 80f, h * 0.62f)
                font.data.setScale(1.2f)
                font.draw(ui, "WAVE " + wave + "   SCORE " + score + "   KILLS " + kills, w / 2f - 120f, h * 0.54f)
                font.draw(ui, "TAP TO PLAY AGAIN", w / 2f - 105f, h * 0.43f)
            }
        }
        ui.end()
        font.data.setScale(1.15f)
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
        batch.dispose()
        assets.dispose()
        ui.dispose()
        font.dispose()
        shapes.dispose()
    }

    private inner class GameInput : com.badlogic.gdx.InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val w = Gdx.graphics.width
            val h = Gdx.graphics.height
            if (state == State.MENU || state == State.GAME_OVER) {
                startGame()
                return true
            }
            if (state == State.PAUSED) {
                state = State.PLAYING
                return true
            }
            val y = h - screenY
            if (screenX > w * 0.70f && y < h * 0.30f) {
                if (screenX > w * 0.82f) pulse() else nova()
                abilityPointer = pointer
                return true
            }
            if (screenX < w * 0.48f && y < h * 0.45f) {
                joystickPointer = pointer
                lastTouchX = screenX.toFloat()
                lastTouchY = screenY.toFloat()
                return true
            }
            lookPointer = pointer
            lastTouchX = screenX.toFloat()
            lastTouchY = screenY.toFloat()
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (pointer == joystickPointer) {
                val dx = (screenX - lastTouchX) / 70f
                val dy = (screenY - lastTouchY) / 70f
                joyX = MathUtils.clamp(joyX + dx, -1f, 1f)
                joyY = MathUtils.clamp(joyY - dy, -1f, 1f)
            } else if (pointer == lookPointer) {
                val dx = screenX - lastTouchX
                player.yaw -= dx * 0.0065f
            }
            lastTouchX = screenX.toFloat()
            lastTouchY = screenY.toFloat()
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer == joystickPointer) {
                joystickPointer = -1
                joyX = 0f
                joyY = 0f
            }
            if (pointer == lookPointer) lookPointer = -1
            if (pointer == abilityPointer) abilityPointer = -1
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
            return false
        }
    }

    private inner class GameAssets : Disposable {
        private val mb = ModelBuilder()
        private val attr = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        private val models = ArrayList<Model>()

        private fun material(c: Color) = Material(ColorAttribute.createDiffuse(c))

        val ground: ModelInstance
        val player: ModelInstance
        val zombie: ModelInstance
        val zombieHead: ModelInstance
        val pickup: ModelInstance
        val effect: ModelInstance
        val props = ArrayList<ModelInstance>()

        init {
            val groundModel = mb.createBox(52f, 0.25f, 52f, material(Color(0.12f, 0.18f, 0.14f, 1f)), attr)
            val playerModel = mb.createCylinder(0.9f, 1.8f, 0.9f, 10, material(Color(0.18f, 0.72f, 1f, 1f)), attr)
            val zombieModel = mb.createCylinder(1.0f, 1.8f, 1.0f, 10, material(Color(0.34f, 0.68f, 0.25f, 1f)), attr)
            val headModel = mb.createSphere(1.18f, 1.18f, 1.18f, 10, 10, material(Color(0.46f, 0.78f, 0.3f, 1f)), attr)
            val pickupModel = mb.createSphere(0.7f, 0.7f, 0.7f, 8, 8, material(Color(0.2f, 0.85f, 1f, 1f)), attr)
            val effectModel = mb.createCylinder(1f, 0.08f, 1f, 20, material(Color(0.25f, 0.75f, 1f, 0.55f)), attr)
            models.addAll(listOf(groundModel, playerModel, zombieModel, headModel, pickupModel, effectModel))

            ground = ModelInstance(groundModel).apply { transform.setToTranslation(0f, -0.15f, 0f) }
            player = ModelInstance(playerModel)
            zombie = ModelInstance(zombieModel)
            zombieHead = ModelInstance(headModel)
            pickup = ModelInstance(pickupModel)
            effect = ModelInstance(effectModel)

            for (i in 0 until 22) {
                val x = ((i * 17) % 45) - 22f
                val z = ((i * 29) % 45) - 22f
                if (kotlin.math.abs(x) < 4f && kotlin.math.abs(z) < 4f) continue
                val m = mb.createBox(
                    1.2f + (i % 3) * 0.7f,
                    1.0f + (i % 4) * 0.6f,
                    1.2f + (i % 2) * 0.8f,
                    material(if (i % 2 == 0) Color(0.22f, 0.27f, 0.3f, 1f) else Color(0.16f, 0.31f, 0.23f, 1f)),
                    attr
                )
                models += m
                props += ModelInstance(m).apply { transform.setToTranslation(x, 0.55f, z) }
            }
        }

        override fun dispose() {
            models.forEach { it.dispose() }
        }
    }

    private class Player {
        val pos = Vector3(0f, 0.9f, 0f)
        var yaw = 0f
        var health = 100f
        var maxHealth = 100f
        var energy = 100f
        var speed = 4.5f
        var pulseCooldown = 0f
        var novaCooldown = 0f
        fun reset() {
            pos.set(0f, 0.9f, 0f)
            yaw = 0f
            health = 100f
            maxHealth = 100f
            energy = 100f
            speed = 4.5f
            pulseCooldown = 0f
            novaCooldown = 0f
        }
    }

    private class Zombie(val pos: Vector3, var health: Float, val speed: Float, val damage: Float) {
        var attackTimer = 0f
        var dead = false
    }

    private class Pickup(val pos: Vector3, val type: PickupType) { var age = 0f }

    private class Effect(val pos: Vector3, val life: Float, val maxRadius: Float, val type: EffectType) {
        var age = 0f
        var radius = 0f
    }
}
