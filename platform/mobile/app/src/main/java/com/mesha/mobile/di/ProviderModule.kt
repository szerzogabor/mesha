package com.mesha.mobile.di

import com.mesha.mobile.domain.ai.GemmaLocalAiProvider
import com.mesha.mobile.domain.ai.LiteRtLmLocalAiProvider
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.domain.ai.LocalAiProviderRouter
import com.mesha.mobile.domain.speech.AndroidSpeechInputProvider
import com.mesha.mobile.domain.speech.SpeechInputProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

/**
 * Binds the on-device AI and speech abstractions to their concrete implementations.
 *
 * Two inference engines back [LocalAiProvider] today — MediaPipe ([GemmaLocalAiProvider]) and
 * LiteRT-LM ([LiteRtLmLocalAiProvider]) — because no single engine's models fit every device.
 * Each is contributed to a `Map<String, LocalAiProvider>` keyed by the same `engine` string used
 * in [com.mesha.mobile.localai.model.InstalledModel]. [LocalAiProviderRouter] is the unqualified
 * [LocalAiProvider] that feature code actually depends on; it picks whichever engine's model is
 * installed. Adding a third engine is a two-line addition here, nothing else.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    @Binds
    @IntoMap
    @StringKey("mediapipe")
    abstract fun bindGemmaProvider(impl: GemmaLocalAiProvider): LocalAiProvider

    @Binds
    @IntoMap
    @StringKey("litertlm")
    abstract fun bindLiteRtLmProvider(impl: LiteRtLmLocalAiProvider): LocalAiProvider

    @Binds
    @Singleton
    abstract fun bindLocalAiProvider(impl: LocalAiProviderRouter): LocalAiProvider

    @Binds
    @Singleton
    abstract fun bindSpeechInputProvider(impl: AndroidSpeechInputProvider): SpeechInputProvider
}
