# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Jira / Ticket Workflow

Use the `/work-ticket` skill to pick up and implement tickets. All Jira access details, the ticket lifecycle workflow, PR monitoring rules, and review comment handling are defined there.

## Git Development Branch Requirements

You are working on the following feature branches:

- **szerzogabor/mesh**: Develop on branch `claude/copy-mesh-config-files-PaGSp`
- **szerzogabor/mesha**: Develop on branch `claude/copy-mesh-config-files-PaGSp`

### Important Instructions:

1. **DEVELOP** all your changes on the designated branch above
2. **COMMIT** your work with clear, descriptive commit messages
3. **PUSH** to the specified branch when your changes are complete
4. **CREATE** the branch locally if it doesn't exist yet
5. **NEVER** push to a different branch without explicit permission

Remember: All development and final pushes should go to the branches specified above.

## Git Operations

Follow these practices for git:

**For git push:**
- Always use git push -u origin <branch-name>
- Only if push fails due to network errors retry up to 4 times with exponential backoff (2s, 4s, 8s, 16s)
- Example retry logic: try push, wait 2s if failed, try again, wait 4s if failed, try again, etc.
- IMPORTANT: After pushing your changes, ALWAYS create a pull request for the pushed branch if one does not already exist. Create the pull request as ready for review (not a draft). You do not need to ask the user first.

**For git fetch/pull:**
- Prefer fetching specific branches: git fetch origin <branch-name>
- If network failures occur, retry up to 4 times with exponential backoff (2s, 4s, 8s, 16s)
- For pulls use: git pull origin <branch-name>
