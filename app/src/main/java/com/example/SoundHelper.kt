package com.example

import android.media.AudioManager
import android.media.ToneGenerator

object SoundHelper {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    fun playPresentSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
    }

    fun playAbsentSound() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
    }

    fun playSwipeSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
    }
}
