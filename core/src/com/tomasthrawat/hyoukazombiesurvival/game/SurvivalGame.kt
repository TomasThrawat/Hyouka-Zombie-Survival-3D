package com.tomasthrawat.hyoukazombiesurvival.game

import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.*
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.*

class SurvivalGame : ApplicationAdapter() {
    private lateinit var batch: ModelBatch
    private lateinit var models: GameModels
    private lateinit var environment: Environment
    private lateinit var camera: PerspectiveCamera
    private lateinit var ui: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var shapes: ShapeRenderer
    private val player = Vector3(0f, 1f, 0f)
    private val zombies = ArrayList<Zombie>()
    private val pulses = ArrayList<Pulse>()
    private var yaw = 0f
    private var health = 100f
    private var energy = 100f
    private var wave = 1
    private var score = 0
    private var spawnTimer = 0f
    private var waveTimer = 0f
    private var damageTimer = 0f
    private var joystickX = 0f
    private var joystickY = 0f
    private var draggingMove = false
    private var lastX = 0f
    private var lastY = 0f
    private var gameOver = false

    override fun create() {
        batch = ModelBatch()
        models = GameModels()
        environment = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.72f, 0.78f, 0.72f, 1f))
            add(DirectionalLight().set(0.9f, 0.9f, 0.82f, -0.5f, -1f, -0.35f))
        }
        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply { near = 0.1f; far = 140f }
        ui = SpriteBatch()
        font = BitmapFont()
        font.data.setScale(1.35f)
        shapes = ShapeRenderer()
        Gdx.input.inputProcessor = Input()
        spawnWave()
        Gdx.graphics.setContinuousRendering(true)
    }

    override fun render() {
        val dt = min(Gdx.graphics.deltaTime, 0.033f)
        if (!gameOver) update(dt)
        Gdx.gl.glClearColor(0.035f, 0.055f, 0.07f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        camera.position.set(player.x - sin(yaw) * 7f, player.y + 5f, player.z - cos(yaw) * 7f)
        camera.lookAt(player.x, player.y + 0.8f, player.z)
        camera.update()
        batch.begin(camera)
        drawWorld()
        batch.end()
        drawHud()
    }

    private fun update(dt: Float) {
        val forward = Vector3(-sin(yaw), 0f, -cos(yaw))
        val right = Vector3(cos(yaw), 0f, -sin(yaw))
        val move = Vector3().mulAdd(forward, joystickY).mulAdd(right, joystickX)
        if (move.len2() > 0.001f) player.mulAdd(move.nor(), 4.5f * dt)
        player.x = player.x.coerceIn(-24f, 24f)
        player.z = player.z.coerceIn(-24f, 24f)
        spawnTimer += dt
        waveTimer += dt
        damageTimer -= dt
        energy = min(100f, energy + 8f * dt)
        if (spawnTimer > max(1.2f, 3.2f - wave * 0.12f) && zombies.size < 4 + wave * 2) { spawnZombie(); spawnTimer = 0f }
        if (waveTimer > 28f) { wave++; waveTimer = 0f; spawnTimer = 0f }
        for (z in zombies) {
            val toPlayer = Vector3(player).sub(z.pos)
            val d = toPlayer.len()
            if (d > 1.35f) z.pos.mulAdd(toPlayer.nor(), (0.75f + wave * 0.055f) * dt)
            if (d < 1.7f && damageTimer <= 0f) { health -= 8f; damageTimer = 0.7f; if (health <= 0f) gameOver = true }
            z.body.transform.setTranslation(z.pos)
            z.head.transform.setTranslation(z.pos.x, z.pos.y + 1.15f, z.pos.z)
        }
        val pit = pulses.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.age += dt
            p.radius = min(6.5f, p.age * 10f)
            if (!p.hit) {
                for (z in zombies) if (z.pos.dst(p.pos) < p.radius) z.health = 0f
                p.hit = true
            }
            p.instance.transform.setToTranslation(p.pos).scale(p.radius, p.radius, p.radius)
            if (p.age > 0.7f) pit.remove()
        }
        val dead = zombies.filter { it.health <= 0f }
        if (dead.isNotEmpty()) { score += dead.size * 10; zombies.removeAll(dead.toSet()) }
    }

    private fun spawnWave() { repeat(5) { spawnZombie() } }
    private fun spawnZombie() {
        val angle = MathUtils.random(0f, MathUtils.PI2)
        val radius = MathUtils.random(10f, 18f)
        val pos = Vector3(cos(angle) * radius, 1f, sin(angle) * radius)
        val body = models.zombieBody().also { it.transform.setTranslation(pos) }
        val head = models.zombieHead().also { it.transform.setTranslation(pos.x, pos.y + 1.15f, pos.z) }
        zombies.add(Zombie(pos, body, head))
    }
    private fun firePulse() {
        if (energy < 25f || gameOver) return
        energy -= 25f
        val dir = Vector3(-sin(yaw), 0f, -cos(yaw))
        pulses.add(Pulse(Vector3(player).mulAdd(dir, 1.3f), models.pulse()))
    }
    private fun drawWorld() {
        batch.render(models.ground)
        models.player.transform.setTranslation(player)
        batch.render(models.player)
        for (z in zombies) { batch.render(z.body); batch.render(z.head) }
        for (p in pulses) batch.render(p.instance)
        for (prop in models.props) batch.render(prop)
    }
    private fun drawHud() {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.setColor(0f, 0f, 0f, 0.42f); shapes.rect(18f, h - 92f, 350f, 72f)
        shapes.setColor(0.2f, 0.85f, 0.35f, 1f); shapes.rect(30f, h - 58f, 220f * (health / 100f), 14f)
        shapes.setColor(0.2f, 0.65f, 1f, 1f); shapes.rect(30f, h - 82f, 220f * (energy / 100f), 10f)
        shapes.setColor(0.15f, 0.85f, 1f, 0.32f); shapes.circle(w - 92f, 92f, 64f, 32)
        shapes.end()
        ui.begin()
        font.draw(ui, "WAVE $wave   SCORE $score", 30f, h - 26f)
        font.draw(ui, "ENERGY PULSE", w - 155f, 86f)
        if (gameOver) {
            font.data.setScale(2.2f); font.draw(ui, "SURVIVAL OVER", w / 2f - 115f, h / 2f + 20f)
            font.data.setScale(1.35f); font.draw(ui, "Tap the pulse circle to restart", w / 2f - 135f, h / 2f - 20f)
        }
        ui.end()
    }
    override fun dispose() { batch.dispose(); models.dispose(); ui.dispose(); font.dispose(); shapes.dispose() }

    private inner class Input : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (gameOver && screenX > Gdx.graphics.width * 0.7f && screenY > Gdx.graphics.height * 0.65f) {
                health = 100f; energy = 100f; score = 0; wave = 1; waveTimer = 0f; zombies.clear(); pulses.clear(); gameOver = false; spawnWave(); return true
            }
            if (screenX > Gdx.graphics.width * 0.72f && screenY > Gdx.graphics.height * 0.62f) { firePulse(); return true }
            if (screenX < Gdx.graphics.width * 0.52f) { draggingMove = true; lastX = screenX.toFloat(); lastY = screenY.toFloat(); return true }
            lastX = screenX.toFloat(); lastY = screenY.toFloat(); return true
        }
        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            val dx = screenX - lastX
            val dy = screenY - lastY
            if (draggingMove) { joystickX = (dx / 80f).coerceIn(-1f, 1f); joystickY = (-dy / 80f).coerceIn(-1f, 1f) } else { yaw -= dx * 0.008f }
            lastX = screenX.toFloat(); lastY = screenY.toFloat(); return true
        }
        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean { draggingMove = false; joystickX = 0f; joystickY = 0f; return true }
    }
    private class Zombie(val pos: Vector3, val body: ModelInstance, val head: ModelInstance) { var health = 1f }
    private class Pulse(val pos: Vector3, val instance: ModelInstance) { var age = 0f; var radius = 0.1f; var hit = false }
    private class GameModels {
        private val mb = ModelBuilder()
        private val attr = VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal
        private fun mat(c: Color) = Material(ColorAttribute.createDiffuse(c))
        val ground = ModelInstance(mb.createBox(52f, 0.2f, 52f, mat(Color(0.16f,0.20f,0.16f,1f)), attr.toLong()))
        val player = ModelInstance(mb.createCylinder(0.9f, 1.8f, 0.9f, 12, mat(Color(0.25f,0.75f,1f,1f)), attr.toLong()))
        val props = ArrayList<ModelInstance>()
        init {
            for (i in 0 until 24) {
                val x = (i * 17 % 47) - 23f
                val z = (i * 31 % 47) - 23f
                props += ModelInstance(mb.createBox(1.4f, 2f + (i % 3), 1.4f, mat(Color(0.24f,0.28f,0.30f,1f)), attr.toLong())).also { it.transform.setToTranslation(x,1f + (i%3)*0.5f,z) }
            }
        }
        fun zombieBody() = ModelInstance(mb.createCylinder(1.0f, 1.8f, 1.0f, 10, mat(Color(0.35f,0.58f,0.20f,1f)), attr.toLong()))
        fun zombieHead() = ModelInstance(mb.createSphere(1.25f,1.25f,1.25f,12,12,mat(Color(0.48f,0.72f,0.25f,1f)),attr.toLong()))
        fun pulse() = ModelInstance(mb.createSphere(1f,1f,1f,12,12,mat(Color(0.25f,0.8f,1f,0.6f)),attr.toLong()))
        fun dispose() {}
    }
}
