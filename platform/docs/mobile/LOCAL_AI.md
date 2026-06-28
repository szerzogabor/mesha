# Mesha Mobile — Local AI Model Management

Native, on-device AI model management. Mesha discovers, downloads, verifies, installs,
updates and deletes supported local AI models itself — with **no dependency on the AI Edge
Gallery app**. Models are fetched directly from their provider (Hugging Face today, any
provider tomorrow) using a backend-served catalog as the single source of truth.

> Scope: this feature is **model management only**. Inference (prompt execution, chat,
> reasoning) is unchanged and out of scope — see [`GEMMA_INTEGRATION.md`](GEMMA_INTEGRATION.md).

---

## Architecture

```
Mesha Backend ──GET /api/local-ai/models──► Model Catalog (source of truth)
        │
        ▼
Android app
  ModelCatalogRepository ──► caches catalog, detects updates
  ModelDownloadManager   ──► resumable download + SHA-256 verify + atomic install
  ModelStorageManager    ──► installed models, disk usage, free space (app-specific storage)
  ModelRepository        ──► high-level API used by the ViewModel
        │
        ▼
Local storage: Android/data/com.mesha.mobile/files/models/<id>/
```

The mobile app **never hardcodes model URLs** — every field (download URL, checksum, size,
RAM/storage requirements, engine) comes from the backend catalog.

### Backend

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /api/local-ai/models` | public | Full catalog of supported models |
| `GET /api/local-ai/models/{id}` | public | One catalog entry |

The catalog is served by `LocalAiCatalogService` from a curated built-in list, and can be
extended or overridden per-deployment via `mesha.local-ai.*` configuration
(`LocalAiCatalogProperties`) — e.g. to repoint a model at a Mesha CDN mirror or correct a
checksum without a code change:

```yaml
mesha:
  local-ai:
    include-defaults: true
    models:
      - id: my-model
        name: My Model
        provider: Google
        source: huggingface
        engine: mediapipe
        file-name: my-model.task
        size-bytes: 123456789
        sha256: <hex>
        download-url: https://example.com/my-model.task
        minimum-ram-gb: 6
        minimum-storage-gb: 5
        recommended: true
```

### Android module (`com.mesha.mobile.localai`)

```
localai/
  api/        LocalAiApi (Retrofit), LocalAiModelDto
  catalog/    ModelCatalogRepository — load + cache + offline fallback
  download/   ModelDownloadManager — resume, progress, cancel, verify, atomic install
  storage/    ModelStorageManager — installed models, disk usage, free space
  repository/ ModelRepository — installModel / removeModel / installedModels /
              availableModels / checkForUpdates
  model/      LocalAiModel, InstalledModel, CatalogEntry, ModelStatus, DownloadState
  ui/         LocalAiScreen, LocalAiViewModel, components/ModelDownloadRow
  util/       Sha256, formatBytes
```

The catalog API reuses the shared Retrofit/OkHttp stack; everything else is a plain Hilt
`@Inject` singleton (`LocalAiModule` only provides the `LocalAiApi`).

---

## Download flow

```
Download ─► check free space ─► stream to <file>.part (resumable via HTTP Range)
        ─► SHA-256 verify ─► move .part into place ─► write metadata.json ─► Installed
```

- **Resumable** — bytes stream into `<file>.part`; a retry sends `Range: bytes=<existing>-`.
  A `206 Partial Content` continues where it left off; a `200 OK` restarts cleanly.
- **Pause / cancel** — cancelling the coroutine stops the transfer but **keeps** the partial
  file, so the next attempt resumes.
- **Progress** — `DownloadState.Downloading(bytesDownloaded, totalBytes)` with a `fraction`.
- **Checksum** — the assembled file is hashed end-to-end and compared to the catalog
  `sha256`. A blank catalog checksum skips verification. On mismatch the partial file is
  deleted and the user can retry.
- **Atomic install** — the model is only considered installed once the verified file is
  moved into place and `metadata.json` is written.

## Storage

Everything lives under **app-specific external storage**
(`Android/data/com.mesha.mobile/files/models/`), so:

- **No runtime storage permission** is ever requested.
- The OS reclaims the space on uninstall.

Per-model layout:

```
models/<id>/
  <model file>      e.g. gemma-3n-E2B-it-int4.task
  metadata.json     InstalledModel — the source of truth for "installed"
  checksum.txt      verified SHA-256
```

The **filesystem (not a database)** is the source of truth, so installed models survive app
restarts automatically (`ModelStorageManager.installedModels()` scans the models root).

## Update detection

Opening Local AI loads the catalog and compares each installed model's `version` against the
catalog `version`. A mismatch surfaces an **Update** action
(`ModelStatus.UpdateAvailable`); `ModelRepository.checkForUpdates()` force-refreshes the
catalog and returns the outdated set.

## Error handling

`DownloadError` categorises failures for the UI: `NETWORK_UNAVAILABLE`, `DOWNLOAD_TIMEOUT`,
`DOWNLOAD_INTERRUPTED`, `INSUFFICIENT_STORAGE`, `CHECKSUM_MISMATCH`,
`CORRUPTED_INSTALLATION`, `UNKNOWN`. Free space (plus a safety margin) is checked **before**
a download starts.

## Engine / provider agnosticism

`LocalAiModel.engine` and `.source` are plain strings, and the download manager only moves
bytes and verifies a hash — it neither knows nor cares whether the artifact is a MediaPipe
`.task`, ONNX, GGUF (llama.cpp) or MLC file. New providers (Hugging Face, Google AI, Ollama
repos, a Mesha CDN) and engines are added by extending the **backend catalog only** — no
mobile refactor required.

### Bridge to inference

`GemmaModelManager.resolveModelFile()` also scans `models/<id>/` subdirectories, so a model
installed through this feature is automatically picked up by the existing on-device Gemma
inference path with no inference-code change.

---

## Tests

| Test | Covers |
|------|--------|
| `LocalAiCatalogServiceTest` (backend) | catalog defaults, ordering, override, lookup |
| `Sha256Test` | digest correctness + verification skip |
| `ModelStorageManagerTest` | metadata round-trip, install listing, delete, disk usage, space check |
| `ModelCatalogRepositoryTest` | mapping, caching, force-refresh, offline fallback |
| `ModelRepositoryTest` | install-status mapping, update detection |
| `ModelDownloadManagerTest` | progress, checksum verify/mismatch, insufficient storage, **resume via HTTP Range** |
