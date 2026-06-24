# Offline Mode & Draft Synchronization

Users can generate and save issue drafts with no network. Draft creation is local-first;
syncing to the backend is a separate, retryable step.

## Flow

```
Prompt (typed/voice) → Gemma (local) → IssueDraft → review/edit
        → DraftRepository.saveDraft()  [Room, status = PENDING]   ← always succeeds offline
        → syncPending()
              ├─ online  → POST /api/projects/{id}/issues → status = SYNCED
              └─ offline → status = FAILED + DraftSyncWorker enqueued (network constraint)
```

The user's work is **persisted before any network call**, so a failed/absent connection
queues the draft rather than losing it.

## Persistence

Room table `drafts` (`DraftEntity`) holds the prompt, the editable draft fields,
acceptance criteria/labels as JSON, and a sync state machine:

```
PENDING → SYNCING → SYNCED
                 └→ FAILED → (retry) → SYNCING → …
```

`syncAttempts` is capped (5) so a permanently-bad draft stops retrying. On sync, label
**names** are resolved to workspace label **ids**, and acceptance criteria are appended to
the issue description as a markdown checklist.

## Retry / background sync

`DraftSyncWorker` (WorkManager, `NetworkType.CONNECTED`, Hilt-injected) drains the queue:

- enqueued on app start (`MainActivity`) and after each offline create;
- WorkManager runs it once connectivity is available, surviving process death;
- two-layer retry: WorkManager `Result.retry()` (transient) + per-draft attempt counter.

The Home screen shows a live "N drafts waiting to sync" indicator
(`observeUnsyncedCount`).

## Tests

`DraftRepositoryTest` covers: save→PENDING, success→SYNCED (with remote id capture),
failure→FAILED + attempt increment, retry-then-succeed, and the max-attempts cap — all
with an in-memory fake DAO and a mocked API.
