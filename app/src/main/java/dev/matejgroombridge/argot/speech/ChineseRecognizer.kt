package dev.matejgroombridge.argot.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper over Android's SpeechRecognizer configured for zh-CN, mirroring
 * the web app's use of the Web Speech API: partial transcripts stream in, a
 * final transcript arrives at the end, and errors are split into "heard
 * nothing" (retryable) vs "mic hiccup" (offer self-grading).
 */
class ChineseRecognizer(private val context: Context) {

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null

    fun start(
        onPartial: (String) -> Unit,
        onEnd: (finalText: String) -> Unit,
        onError: () -> Unit,
    ) {
        cancel()
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        var latest = ""
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                latest = text
                onPartial(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: latest
                cleanup()
                onEnd(text)
            }

            override fun onError(error: Int) {
                cleanup()
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onEnd(latest) // nothing recognisable said
                    else -> onError()
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            rec.startListening(intent)
        } catch (_: Exception) {
            cleanup()
            onError()
        }
    }

    /** Stop capturing and let recognition finish (delivers onResults). */
    fun stop() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
    }

    /** Abort without delivering results. */
    fun cancel() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        cleanup()
    }

    private fun cleanup() {
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }
}
