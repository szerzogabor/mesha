# Mesha Mobile — Architecture

The Android app is a first-class Mesha platform component living at
[`platform/mobile/`](../../mobile/). It reuses the existing Mesha REST API and adds
on-device AI so issues can be created without any cloud AI provider.

## Stack

| Concern | Choice |
|---------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (dark mode, dynamic color, large-screen aware) |
| Architecture | MVVM (unidirectional state via `StateFlow`) |
| DI | Hilt |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Persistence | Room (offline draft queue) |
| Auth | Clerk Android SDK (`com.clerk:clerk-android-ui`) — hosted sign-in UI + session storage |
| Background | WorkManager (`DraftSyncWorker`) |
| Async | Coroutines / Flow |
| On-device LLM | Google AI Edge / MediaPipe `tasks-genai` (Gemma) |
| Target | Android 13+ (minSdk 33, target/compileSdk 35) |

## Layers

```
ui (Compose screens + ViewModels)
        │  StateFlow<UiState>
data.repository (Auth, Mesha, Draft, Selection)
        │
 ┌──────┴───────┐
 remote          local
 (MeshaApi)      (Room drafts)
        │
domain (LocalAiProvider/Gemma, SpeechInputProvider) — pure, swappable
```

- **ViewModels** expose immutable `UiState` data classes and never touch Retrofit/Room
  directly — only repositories.
- **Repositories** wrap all I/O in `Result<T>` on `Dispatchers.IO`.
- **Domain abstractions** (`LocalAiProvider`, `SpeechInputProvider`) keep feature code
  independent of Gemma/MediaPipe and the platform recognizer; implementations are bound
  in `di/ProviderModule.kt`.

## Authentication

Mesha auth is unchanged: every request carries `Authorization: Bearer <clerk-jwt>`. The
**Clerk Android SDK** (`Clerk.initialize` in `MeshaApplication`) owns sign-in/sign-up UI
and session persistence — `LoginScreen` is a thin wrapper around Clerk's `AuthView`,
which renders the full hosted flow (email, OAuth, passkeys, MFA) with no app-specific
deep link or manifest entries required.

`AuthRepository` observes `Clerk.userFlow`: whenever it emits a non-null user it calls
`POST /api/auth/sync` to keep the backend's user record current and flips `AuthState` to
`Authenticated`; when it's null, `AuthState` flips to `Unauthenticated`. `AuthInterceptor`
fetches a fresh JWT from `Clerk.session?.fetchToken()` on every request (Clerk tokens are
short-lived and the SDK caches/refreshes them internally) rather than reading one from
storage. No mobile-specific auth endpoints were added.

## Backend integration

The app binds to existing controllers only (see `data/remote/MeshaApi.kt`):

| Feature | Endpoint |
|---------|----------|
| Login sync | `POST /api/auth/sync` |
| Workspaces | `GET /api/workspaces` |
| Projects | `GET /api/workspaces/{id}/projects` |
| Labels | `GET /api/workspaces/{id}/labels` |
| Issues | `GET/POST /api/projects/{id}/issues` |
| Comments | `GET/POST /api/issues/{id}/comments` |
| Agents | `GET /api/workspaces/{id}/agents/active` |
| Sessions | `GET /api/agent-sessions`, `GET .../{id}`, messages |
| Follow-ups | `POST /api/agent-sessions/{id}/messages` |
| Update check | `GET /api/releases/android/latest` (public) |

The only new backend surface is the **release-management** system (APK distribution),
which is shared with the web download page — not mobile-specific. See
[APK_DISTRIBUTION.md](./APK_DISTRIBUTION.md).

## Navigation & features

Bottom navigation (Material 3 `NavigationBar`): **Home, Issues, Sessions, Projects,
Settings**, plus an Agents screen and the full-screen *Create Issue with AI* flow.

- **Home** — workspace stats + the primary "Create Issue with AI" action and a queued-
  draft indicator.
- **Issues** — per-project issue list, AI-create FAB.
- **Sessions** — live AI sessions with status, logs, PR, and **follow-up messages** to
  continue a session from mobile.
- **Agents** — status (online/offline), executor type (Qwen CLI, Claude Code, Codex CLI,
  future executors).
- **Settings** — on-device model status, in-app update check/install, sign out.

See also: [GEMMA_INTEGRATION.md](./GEMMA_INTEGRATION.md),
[OFFLINE_MODE.md](./OFFLINE_MODE.md), [INSTALLATION.md](./INSTALLATION.md),
[RELEASE_PROCESS.md](./RELEASE_PROCESS.md).
