package nl.myquest.nowindcompanion

import android.media.AudioManager
import android.media.ToneGenerator

fun beep() {
    val durationInMs = 1000 // Duration in milliseconds

    val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100) // Adjust volume if needed

    toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, durationInMs) // Plays tone with the specified frequency and duration

    // Delay execution to let the sound play for the specified duration
    Thread.sleep(durationInMs.toLong())

    // Release resources when done
    toneGenerator.release()
}
