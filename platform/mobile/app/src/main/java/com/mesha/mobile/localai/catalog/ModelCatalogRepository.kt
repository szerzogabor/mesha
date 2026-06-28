package com.mesha.mobile.localai.catalog

import com.mesha.mobile.localai.api.LocalAiApi
import com.mesha.mobile.localai.model.LocalAiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and caches the backend model catalog. The cache lets the Local AI screen render
 * install state instantly on revisit and tolerate transient offline moments — a failed
 * refresh falls back to the last known catalog rather than blanking the screen.
 */
@Singleton
class ModelCatalogRepository @Inject constructor(
    private val api: LocalAiApi,
) {
    private val mutex = Mutex()
    @Volatile
    private var cached: List<LocalAiModel>? = null

    /**
     * Returns the catalog. Uses the in-memory cache unless [forceRefresh] is set. On network
     * failure, falls back to the cache when available, otherwise propagates the failure.
     */
    suspend fun getCatalog(forceRefresh: Boolean = false): Result<List<LocalAiModel>> {
        cached?.let { if (!forceRefresh) return Result.success(it) }
        return withContext(Dispatchers.IO) {
            runCatching { api.getModels().map { it.toDomain() } }
                .onSuccess { fresh -> mutex.withLock { cached = fresh } }
                .recoverCatching { error -> cached ?: throw error }
        }
    }

    /** Last loaded catalog without hitting the network, or empty if never loaded. */
    fun cachedCatalog(): List<LocalAiModel> = cached.orEmpty()
}
