package com.mesha.api.dto;

/**
 * Catalog entry describing an on-device AI model the Mesha mobile app can download,
 * install and run locally.
 *
 * <p>The backend is the single source of truth for supported models — the mobile client
 * never hardcodes download URLs. Each entry is engine- and provider-agnostic so the same
 * catalog can describe MediaPipe {@code .task} bundles, ONNX, llama.cpp GGUF, etc., served
 * from Hugging Face today and any other provider in the future.
 *
 * @param id               stable catalog id, e.g. {@code gemma-3n-e2b}
 * @param name             human-friendly display name
 * @param provider         model author/owner, e.g. {@code Google}
 * @param source           where the file is hosted, e.g. {@code huggingface}
 * @param version          catalog version string; bumped to signal an available update
 * @param engine           inference engine the file targets, e.g. {@code mediapipe}
 * @param fileName         on-disk filename to store the downloaded artifact as
 * @param sizeBytes        download size in bytes (for storage pre-checks and progress)
 * @param sha256           lowercase hex SHA-256 of the file; empty if not verified
 * @param downloadUrl      absolute URL to fetch the model artifact from
 * @param licenseUrl       optional URL to the model license/terms
 * @param minimumRamGb     minimum device RAM (GB) recommended to run the model
 * @param minimumStorageGb minimum free storage (GB) required to install the model
 * @param recommended      whether this is the recommended default model
 */
public record LocalAiModelDto(
        String id,
        String name,
        String provider,
        String source,
        String version,
        String engine,
        String fileName,
        long sizeBytes,
        String sha256,
        String downloadUrl,
        String licenseUrl,
        int minimumRamGb,
        int minimumStorageGb,
        boolean recommended
) {
}
