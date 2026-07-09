package dev.matejgroombridge.argot.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.matejgroombridge.argot.data.VOCAB
import java.util.Locale

/**
 * Vocab audio: pre-generated neural TTS bundled in assets/audio as
 * {id}.mp3 + {id}_slow.mp3, with the device's Chinese TTS voice as a
 * fallback for anything not in the bundle — same strategy as the web app.
 */
class AudioService(
    private val context: Context,
    private val rate: () -> Float,
) {
    private val audioByHanzi: Map<String, String> = VOCAB.associate { it.hanzi to it.id }
    private val player = MediaPlayer()
    private var prepared = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var onEndCb: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { onEndCb?.let { cb -> onEndCb = null; cb() } }
                })
            }
        }
    }

    fun stop() {
        onEndCb = null
        try { if (prepared && player.isPlaying) player.pause() } catch (_: Exception) {}
        tts?.stop()
    }

    fun speak(text: String, slow: Boolean = false, onEnd: (() -> Unit)? = null) {
        stop()
        val id = audioByHanzi[text]
        if (id != null) {
            val file = "audio/$id${if (slow) "_slow" else ""}.mp3"
            try {
                player.reset()
                prepared = false
                context.assets.openFd(file).use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                player.prepare()
                prepared = true
                // Slow files are already slowed down; normal files honour the
                // user's voice-speed setting — matches the web behaviour.
                player.playbackParams = PlaybackParams().setSpeed(if (slow) 1f else rate())
                onEndCb = onEnd
                player.setOnCompletionListener { onEndCb?.let { cb -> onEndCb = null; cb() } }
                player.start()
                return
            } catch (_: Exception) {
                // file missing/corrupt → fall through to live TTS
            }
        }
        speakTTS(text, slow, onEnd)
    }

    private fun speakTTS(text: String, slow: Boolean, onEnd: (() -> Unit)?) {
        val t = tts ?: return
        if (!ttsReady) return
        t.setSpeechRate(if (slow) 0.55f else rate())
        onEndCb = onEnd
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "liuli-${System.nanoTime()}")
    }

    fun release() {
        try { player.release() } catch (_: Exception) {}
        tts?.shutdown()
        tts = null
    }
}
