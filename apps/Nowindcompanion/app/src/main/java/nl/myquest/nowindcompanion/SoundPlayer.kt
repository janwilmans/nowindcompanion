package nl.myquest.nowindcompanion

import android.media.AudioManager
import android.media.ToneGenerator

fun io_beep() {
    val durationInMs = 30 // Duration in milliseconds
    val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80) // Adjust volume if needed
    toneGenerator.startTone(ToneGenerator.TONE_CDMA_LOW_SS, durationInMs) // Plays tone with the specified frequency and duration
    // Delay execution to let the sound play for the specified duration
    Thread.sleep(durationInMs.toLong())
    toneGenerator.release()
}

// maybe integrate
// https://github.com/m-abboud/android-tone-player/blob/master/src/main/java/net/mabboud/android_tone_player/TonePlayer.java
fun msx_beep() {
    val durationInMs = 60 // Duration in milliseconds
    val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 90) // Adjust volume if needed
    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, durationInMs) // Plays tone with the specified frequency and duration
    // Delay execution to let the sound play for the specified duration
    Thread.sleep(durationInMs.toLong())
    toneGenerator.release()
}
