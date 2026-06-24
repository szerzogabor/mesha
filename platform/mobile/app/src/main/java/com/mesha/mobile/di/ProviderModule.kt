package com.mesha.mobile.di

import com.mesha.mobile.domain.ai.GemmaLocalAiProvider
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.domain.speech.AndroidSpeechInputProvider
import com.mesha.mobile.domain.speech.SpeechInputProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the on-device AI and speech abstractions to their concrete implementations.
 * Swapping the local model or recognizer is a one-line change here — feature code only
 * depends on the [LocalAiProvider] / [SpeechInputProvider] interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    @Binds
    @Singleton
    abstract fun bindLocalAiProvider(impl: GemmaLocalAiProvider): LocalAiProvider

    @Binds
    @Singleton
    abstract fun bindSpeechInputProvider(impl: AndroidSpeechInputProvider): SpeechInputProvider
}
