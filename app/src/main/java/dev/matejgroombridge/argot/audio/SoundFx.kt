package dev.matejgroombridge.argot.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthesized UI sound effects — a port of the web app's WebAudio oscillator
 * blips (attack ramp + exponential decay envelopes over sine/triangle waves).
 */
class SoundFx(
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
) {
    private data class Note(val freq: Double, val t0: Double, val dur: Double, val triangle: Boolean = false, val gain: Double = 0.12)

    fun correct() = play(listOf(Note(660.0, 0.0, 0.12), Note(880.0, 0.1, 0.18)))
    fun wrong() = play(listOf(Note(220.0, 0.0, 0.2, triangle = true, gain = 0.1)))
    fun fanfare() = play(listOf(523.0, 659.0, 784.0, 1047.0).mapIndexed { i, f -> Note(f, i * 0.12, 0.25) })
    fun levelup() = play(listOf(392.0, 523.0, 659.0, 784.0, 1047.0).mapIndexed { i, f -> Note(f, i * 0.09, 0.3) })

    private fun play(notes: List<Note>) {
        if (!enabled()) return
        scope.launch(Dispatchers.Default) {
            val sr = 22050
            val total = notes.maxOf { it.t0 + it.dur } + 0.05
            val samples = ShortArray((total * sr).toInt())
            for (n in notes) {
                val start = (n.t0 * sr).toInt()
                val len = (n.dur * sr).toInt()
                val attack = (0.015 * sr).toInt().coerceAtLeast(1)
                for (i in 0 until len) {
                    val idx = start + i
                    if (idx >= samples.size) break
                    val phase = 2.0 * PI * n.freq * i / sr
                    val wave = if (n.triangle) {
                        // triangle from phase
                        val p = (n.freq * i / sr) % 1.0
                        4.0 * abs(p - 0.5) - 1.0
                    } else sin(phase)
                    val env = if (i < attack) i.toDouble() / attack
                    else exp(-5.0 * (i - attack) / (len - attack).coerceAtLeast(1))
                    val v = samples[idx] + wave * env * n.gain * Short.MAX_VALUE
                    samples[idx] = v.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
                }
            }
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                delay((total * 1000).toLong() + 100)
                track.release()
            } catch (_: Exception) {
                // audio unavailable — stay silent
            }
        }
    }
}
