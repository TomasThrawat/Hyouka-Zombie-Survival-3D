package com.tomasthrawat.hyoukazombiesurvival
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.tomasthrawat.hyoukazombiesurvival.game.SurvivalGame
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply { useImmersiveMode = true; numSamples = 2; useCompass = false; useAccelerometer = false }
        initialize(SurvivalGame(), config)
    }
}
