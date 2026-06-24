package com.mesha.mobile.domain.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * [SpeechInputProvider] backed by the platform [SpeechRecognizer]. Bridges the
 * callback-based recognizer into a cold [Flow]; cancelling the collector tears the
 * recognizer down. Must be created and used on the main thread (recognizer requirement),
 * which the ViewModel guarantees by launching collection on the main dispatcher.
 */
class AndroidSpeechInputProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechInputProvider {

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun listen(): Flow<SpeechEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(SpeechEvent.Error("Speech recognition is not available on this device"))
            close()
            return@callbackFlow
        }

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechEvent.ReadyForSpeech)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                if (text != null) trySend(SpeechEvent.Result(text))
                else trySend(SpeechEvent.Error("No speech recognized"))
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Error(describeError(error)))
                close()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        // SpeechRecognizer must be created, started, and destroyed on the main thread,
        // regardless of which dispatcher collects this flow.
        val mainHandler = Handler(Looper.getMainLooper())
        // Only ever touched on the main thread (both posts run there), so no visibility issue.
        var recognizer: SpeechRecognizer? = null
        mainHandler.post {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(listener)
                    startListening(buildIntent())
                }
            } catch (e: Exception) {
                trySend(SpeechEvent.Error("Failed to start speech recognizer: ${e.message}"))
                close()
            }
        }

        awaitClose {
            mainHandler.post {
                recognizer?.stopListening()
                recognizer?.destroy()
            }
        }
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Prefer on-device recognition where supported, keeping the flow offline-friendly.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
        else -> "Speech recognition error ($code)"
    }
}
