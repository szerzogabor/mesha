package com.mesha.mobile.domain.speech

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over speech-to-text so the voice-to-issue flow does not depend on a
 * concrete recognizer. The first implementation wraps Android's on-device
 * [android.speech.SpeechRecognizer]; future implementations (e.g. a Whisper-class
 * on-device model) can be dropped in without touching the UI/ViewModel.
 */
interface SpeechInputProvider {

    /** Whether speech recognition is usable on this device. */
    fun isAvailable(): Boolean

    /**
     * Start listening and emit recognition events until the utterance completes,
     * an error occurs, or the flow is cancelled (which stops the recognizer).
     */
    fun listen(): Flow<SpeechEvent>
}

sealed interface SpeechEvent {
    /** Microphone is active and ready for the user to speak. */
    data object ReadyForSpeech : SpeechEvent

    /** Live partial transcription as the user speaks. */
    data class Partial(val text: String) : SpeechEvent

    /** Final transcription for the utterance. */
    data class Result(val text: String) : SpeechEvent

    /** Recognition failed; [message] is user-presentable. */
    data class Error(val message: String) : SpeechEvent
}
