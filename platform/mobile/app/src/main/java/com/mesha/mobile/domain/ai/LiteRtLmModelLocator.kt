package com.mesha.mobile.domain.ai

import com.mesha.mobile.localai.model.InstalledModel
import com.mesha.mobile.localai.storage.ModelStorageManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locates the on-device LiteRT-LM model file.
 *
 * Unlike [GemmaModelManager], which guesses at sideloaded filenames because Gemma/MediaPipe
 * models can be dropped in by hand or provisioned by the AI Edge Gallery app, LiteRT-LM models
 * are only ever installed through Mesha's catalog/download flow. Resolution is therefore a
 * straightforward lookup of the installed model whose [InstalledModel.engine] matches
 * [ENGINE_ID], via [ModelStorageManager] — the single source of truth for installed models.
 */
@Singleton
class LiteRtLmModelLocator @Inject constructor(
    private val modelStorageManager: ModelStorageManager,
) {
    fun resolveModelFile(): File? {
        val model = selectLiteRtLmModel(modelStorageManager.installedModels()) ?: return null
        return modelStorageManager.modelFile(model)
    }

    fun isModelInstalled(): Boolean = resolveModelFile() != null

    companion object {
        const val ENGINE_ID = "litertlm"
    }
}

/**
 * Picks which installed model to use when resolving the LiteRT-LM engine. Pure function so the
 * selection logic is unit-testable without the native [com.google.ai.edge.litertlm.Engine].
 * If more than one LiteRT-LM model is ever installed at once, the most recently installed wins.
 */
internal fun selectLiteRtLmModel(installed: List<InstalledModel>): InstalledModel? =
    installed
        .filter { it.engine == LiteRtLmModelLocator.ENGINE_ID }
        .maxByOrNull { it.installedAtEpochMs }
