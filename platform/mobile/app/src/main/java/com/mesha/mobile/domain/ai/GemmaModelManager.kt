package com.mesha.mobile.domain.ai

import android.content.Context
import com.mesha.mobile.localai.model.InstalledModel
import com.mesha.mobile.localai.storage.ModelStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locates the on-device Gemma model file.
 *
 * The model is a Google AI Edge / MediaPipe `.task` bundle (the same format the AI Edge
 * Gallery app sideloads). To keep the APK small the weights are NOT shipped in the app;
 * the user places the model in the app's external files dir, or the AI Edge Gallery app
 * provisions it, or the Local AI manager downloads it through the catalog.
 */
@Singleton
class GemmaModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelStorageManager: ModelStorageManager,
) {
    /** Candidate filenames, newest/preferred first, for the legacy/sideloaded layout only. */
    private val candidateNames = listOf(
        "gemma-3n-E2B-it-int4.task",
        "gemma-3n-E4B-it-int4.task",
        "gemma2-2b-it-cpu-int4.task",
        "gemma.task",
    )

    /**
     * Returns the resolved model file if present and non-empty, else null.
     *
     * Prefers the Local AI manager's catalog metadata — the installed model whose declared
     * [InstalledModel.engine] is [ENGINE_ID] — so a `.task`-suffixed file belonging to some
     * *other* installed model/engine is never mistaken for a Gemma bundle (that file would be
     * handed straight to MediaPipe's native loader, which aborts the whole process on an
     * incompatible bundle instead of throwing). Falls back to the legacy sideloaded layout
     * (a `.task` file dropped directly into the models dir, e.g. by the AI Edge Gallery app)
     * only for directories the catalog doesn't track at all.
     */
    fun resolveModelFile(): File? {
        val installedModels = modelStorageManager.installedModels()
        selectMediaPipeModel(installedModels)
            ?.let { modelStorageManager.modelFile(it) }
            ?.let { return it }
        return resolveSideloadedModelFile(installedModels)
    }

    private fun resolveSideloadedModelFile(installedModels: List<InstalledModel>): File? {
        val dirs = listOfNotNull(
            context.getExternalFilesDir("models"),
            File(context.filesDir, "models"),
        )
        val catalogManagedIds = installedModels.map { it.id }.toSet()
        for (dir in dirs) {
            // Direct hits in the models root.
            for (name in candidateNames) {
                val f = File(dir, name)
                if (f.exists() && f.length() > 0) return f
            }
            // Untracked subdirectories only — anything the catalog knows about was already
            // considered (and rejected) above by its declared engine.
            dir.listFiles { f -> f.isDirectory && f.name !in catalogManagedIds }?.forEach { sub ->
                for (name in candidateNames) {
                    val f = File(sub, name)
                    if (f.exists() && f.length() > 0) return f
                }
                sub.listFiles { f -> f.isFile && f.name.endsWith(".task") && f.length() > 0 }
                    ?.firstOrNull()
                    ?.let { return it }
            }
        }
        return null
    }

    fun isModelInstalled(): Boolean = resolveModelFile() != null

    /** Directory the user/AI Edge Gallery should drop the `.task` file into. */
    fun expectedModelDirectory(): File =
        (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).also { it.mkdirs() }

    companion object {
        const val ENGINE_ID = "mediapipe"
    }
}

/**
 * Picks which installed model to use when resolving the MediaPipe engine. Pure function so the
 * selection logic is unit-testable without Android/file-system dependencies, mirroring
 * [selectLiteRtLmModel]. If more than one MediaPipe model is ever installed at once, the most
 * recently installed wins.
 */
internal fun selectMediaPipeModel(installed: List<InstalledModel>): InstalledModel? =
    installed
        .filter { it.engine == GemmaModelManager.ENGINE_ID }
        .maxByOrNull { it.installedAtEpochMs }
