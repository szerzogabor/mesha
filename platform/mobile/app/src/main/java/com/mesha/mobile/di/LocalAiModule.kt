package com.mesha.mobile.di

import com.mesha.mobile.localai.api.LocalAiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Wires the Local AI module's network binding. The catalog API reuses the shared
 * Retrofit/OkHttp stack from [NetworkModule]; download/storage/repository components are
 * plain `@Inject` singletons and need no explicit providers here.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocalAiModule {

    @Provides
    @Singleton
    fun provideLocalAiApi(retrofit: Retrofit): LocalAiApi = retrofit.create(LocalAiApi::class.java)
}
