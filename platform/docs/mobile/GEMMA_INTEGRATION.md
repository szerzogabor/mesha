# On-device Gemma Integration

The app generates issue drafts entirely on-device — no OpenAI, no Claude, no backend AI
processing. Everything runs locally through Google AI Edge / MediaPipe.

## Abstraction

Feature code depends only on `domain/ai/LocalAiProvider`:

```kotlin
interface LocalAiProvider {
    val id: String
    val displayName: String
    suspend fun isAvailable(): Boolean
    suspend fun generateIssueDraft(request: GenerateIssueRequest): IssueDraft
}
```

`GemmaLocalAiProvider` is the concrete implementation, bound in `di/ProviderModule.kt`.
Swapping the model/engine is a one-line binding change; nothing else moves.

## Draft schema

The model is prompted (`IssuePromptBuilder`) to emit exactly:

```json
{ "title": "", "description": "", "acceptanceCriteria": [], "priority": "", "labels": [] }
```

mapped to the `IssueDraft` domain model. `priority` is one of `LOW, MEDIUM, HIGH, URGENT`
(mirrors the backend enum).

## Validation & repair

Local models are messy. `IssueDraftParser` (pure, unit-tested) guarantees a valid draft:

- strips ```` ```json ```` fences and surrounding prose;
- extracts the outermost **balanced** JSON object (brace-aware, ignores braces in strings);
- parses leniently (`ignoreUnknownKeys`, `isLenient`, `coerceInputValues`);
- repairs deviations: `acceptanceCriteria` given as a newline/comma string → array;
  unknown/missing `priority` → `MEDIUM`; missing `title` → derived from the prompt;
  labels/criteria trimmed, de-`#`'d, de-duplicated and capped;
- throws `LocalAiException.InvalidOutput` only when neither title nor description survive.

## Running the model (MediaPipe)

`GemmaLocalAiProvider` lazily builds a single `LlmInference` session from a model file,
guarded by a mutex, and runs `generateResponse` on `Dispatchers.IO`. Failures surface as
typed `LocalAiException`s (`ModelNotAvailable`, `InvalidOutput`, `InferenceFailed`) which
the ViewModel maps to friendly messages.

## Providing the model file

To keep the APK small, weights are **not** bundled. `GemmaModelManager` resolves the
first existing `.task` model from the app's external files dir
(`Android/data/com.mesha.mobile/files/models/`), trying names such as
`gemma-3n-E2B-it-int4.task`, `gemma2-2b-it-cpu-int4.task`, `gemma.task`.

Users provision it by either:
1. installing/exporting a compatible Gemma `.task` model via the **Google AI Edge
   Gallery** app, or
2. copying a `.task` file into the folder shown on the **Settings → On-device AI** screen.

Settings reports install status and the exact target directory; *Refresh status*
re-checks. When no model is present, the Create-with-AI screen disables generation and
points the user to Settings — the rest of the app (issues, sessions, agents) is fully
usable without a model.

## Testing

`IssueDraftParserTest` exercises the repair matrix; `GemmaLocalAiProviderTest` covers
availability and the model-absent failure path without the native runtime. End-to-end
inference is validated by instrumented tests on a device with a model installed.
