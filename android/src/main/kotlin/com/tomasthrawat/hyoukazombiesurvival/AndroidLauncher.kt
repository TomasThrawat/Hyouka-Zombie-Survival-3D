package com.tomasthrawat.hyoukazombiesurvival

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.tomasthrawat.hyoukazombiesurvival.game.SurvivalGame

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            // Use the most conservative Android graphics configuration for
            // broad GLES2/GLES3 device compatibility.
            useImmersiveMode = false
            hideStatusBar = true
            hideNavBar = true
            numSamples = 0
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useRotationVectorSensor = false
            useWakelock = true
            useGL30 = false
        }

        initialize(SurvivalGame(), config)
    }
}
