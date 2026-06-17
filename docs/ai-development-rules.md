# AI Development Rules

All AI agents working on this codebase must follow these rules without exception.

---

## Feature Development Workflow

Before implementing any feature:

1. Read all relevant architecture documents in `docs/architecture/`.
2. Read all relevant feature specifications in `docs/features/`.
3. Identify impacted modules (frontend, backend-api, backend-worker, database).
4. Create or update tests first (TDD).
5. Implement changes.
6. Run all affected tests.
7. Update documentation if behavior changed.

---

## Mandatory Rules

### Code Changes
* Never modify code without understanding the affected modules.
* Never remove existing tests unless explicitly requested.
* Never change API contracts without documenting the change.
* Never change business behavior without updating feature documentation.
* Never add `@Scheduled` jobs to `backend-api` — scheduled work lives only in `backend-worker`.
* Never modify existing Flyway migration files — always add a new `V{n+1}__*.sql`.
* Never auto-merge or auto-deploy — humans must approve all merges.
* Never call the Blocks API directly from business logic — use `ProviderAdapter`.

### Documentation
* Always search for existing feature specifications before implementing changes.
* Always search for existing regression documentation before fixing bugs.
* If documentation and implementation differ, report the discrepancy before proceeding.
* Create missing documentation before implementing major changes.

### Architecture Consistency
* Frontend never accesses the database directly — all data flows through the REST API.
* All API endpoints require `Authorization: Bearer <Clerk JWT>` except `/api/webhooks/*`.
* Webhook endpoints validate HMAC signatures — never skip this check.
* Workspace Blocks API keys are encrypted at rest — never store them as plaintext.

---

## Agent Operating Instructions

Whenever working on a task:

1. Read relevant architecture documents (`docs/architecture/`).
2. Read relevant feature specifications (`docs/features/`).
3. Check existing regression documentation (`docs/regressions/`).
4. Identify impacted modules.
5. Implement the smallest safe change.
6. Run tests.
7. Report:
   - Modified files
   - Updated documentation
   - Affected features
   - Regression risks

If documentation is missing, create it before implementing major changes.

---

## Regression Test Enforcement

When fixing a bug:

1. Search for existing regression documentation in `docs/regressions/`.
2. Create a regression test before implementing the fix.
3. Link the regression test in the bug document (`docs/regressions/BUG-<id>.md`).
4. Verify the bug cannot reoccur.
5. Update regression documentation.

No bug fix should be considered complete without a regression test.

---

## Pull Request Checklist

Before opening a PR:

- [ ] Architecture documents read and understood
- [ ] Feature specifications consulted
- [ ] Regression catalog checked
- [ ] Tests written (or updated) before implementation
- [ ] All affected tests pass
- [ ] Documentation updated if behavior changed
- [ ] No new `@Scheduled` jobs in `backend-api`
- [ ] No modifications to existing Flyway migrations
- [ ] PR title prefixed with ticket ID (e.g., `TP-42: Add feature X`)
- [ ] Only `szerzogabor@gmail.com` in contributor list
