package com.mesha.mobile.domain.ai

import android.content.Context
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
 * provisions it. We resolve the first existing candidate path.
 */
@Singleton
class GemmaModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Candidate filenames, newest/preferred first. */
    private val candidateNames = listOf(
        "gemma-3n-E2B-it-int4.task",
        "gemma-3n-E4B-it-int4.task",
        "gemma2-2b-it-cpu-int4.task",
        "gemma.task",
    )

    /**
     * Returns the resolved model file if present and non-empty, else null.
     *
     * Looks both directly in the models root (legacy / sideloaded layout) and one level
     * deep, so models installed by the Local AI manager — which stores each model under
     * `models/<id>/<file>` — are picked up automatically without changing inference logic.
     */
    fun resolveModelFile(): File? {
        val dirs = listOfNotNull(
            context.getExternalFilesDir("models"),
            File(context.filesDir, "models"),
        )
        for (dir in dirs) {
            // Direct hits in the models root.
            for (name in candidateNames) {
                val f = File(dir, name)
                if (f.exists() && f.length() > 0) return f
            }
            // Local AI manager layout: models/<id>/<.task file>.
            dir.listFiles { f -> f.isDirectory }?.forEach { sub ->
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
}
