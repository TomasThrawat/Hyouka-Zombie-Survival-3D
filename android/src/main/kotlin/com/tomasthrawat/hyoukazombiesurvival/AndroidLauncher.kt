package com.tomasthrawat.hyoukazombiesurvival

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.tomasthrawat.hyoukazombiesurvival.game.SurvivalGame

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = false
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
