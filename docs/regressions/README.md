# Regression Test Catalog

This catalog documents known regressions and bug fixes in the Mesha platform. Every bug fix must have a corresponding entry here and a linked regression test.

---

## Purpose

- Prevent previously fixed bugs from reappearing.
- Provide context for why certain code decisions were made.
- Guide AI agents to check this catalog before fixing bugs.

---

## Rules for Bug Fixes

1. **Search this catalog first** before implementing any bug fix.
2. **Create the regression test before the fix** (TDD).
3. **Add a new entry** (`docs/regressions/BUG-<id>.md`) linking to the test.
4. **Verify the fix** prevents the original issue from reoccurring.
5. **No bug fix is complete** without a regression test and this catalog updated.

---

## Template

Create `docs/regressions/BUG-<id>.md` using this template:

```markdown
# Bug

Description of the original issue.

# Root Cause

Technical explanation.

# Fix

Implemented solution.

# Regression Test

Location of regression test (file path + class + method name).

# Related Features

Affected features (link to docs/features/).

# Notes

Additional context.
```

---

## Known Regressions

| ID | Summary | Fixed In | Regression Test |
|----|---------|----------|----------------|
| BUG-001 | Duplicate GitHub pull request records created on webhook retry | V37 migration (unique constraint on `session_id + pr_number`) | `BlocksAdapterTest#testDuplicatePrWebhookIsIdempotent` |
| BUG-002 | Duplicate Blocks messages inserted on worker restart (missing offset deduplication) | V14 migration (`api_message_offset` column) | `SessionPollTransactionsTokenLimitTest` |

---

## Notes

- Bug IDs are sequential integers, prefixed with `BUG-`.
- When a new bug is found and fixed, assign the next available ID.
- Historical regressions for which no regression test was written must still be documented here; add "No regression test" in the Regression Test column and create one if the code is modified again.
