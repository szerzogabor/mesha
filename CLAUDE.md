# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
- Always use "szerzogabor@gmail.com" as a contributor for commits. This is the ONLY acceptable contributor
- Always use git push -u origin <branch-name>
- Only if push fails due to network errors retry up to 4 times with exponential backoff (2s, 4s, 8s, 16s)
- Example retry logic: try push, wait 2s if failed, try again, wait 4s if failed, try again, etc.
- IMPORTANT: After pushing your changes, ALWAYS create a pull request for the pushed branch if one does not already exist. Create the pull request as ready for review (not a draft). You do not need to ask the user first.

**For git fetch/pull:**
- Prefer fetching specific branches: git fetch origin <branch-name>
- If network failures occur, retry up to 4 times with exponential backoff (2s, 4s, 8s, 16s)
- For pulls use: git pull origin <branch-name>

## Linear Ticket Workflow Rules

All AI agents (Claude Code, Codex, Gemini, etc.) working on this repository MUST follow these rules when working with Linear tickets:

1. **Start working only on tickets in "to do" status** - Do not begin work on tickets that have any other status
2. **Move ticket to "In Progress" when starting** - Immediately after beginning work on a ticket, update its status to "In Progress"
3. Use wok-ticket skill

These rules ensure proper ticket lifecycle management and prevent conflicts with other developers.

## Pull Request Workflow Rules

All AI agents (Claude Code, Codex, Gemini, etc.) working on this repository MUST follow these rules when creating pull requests:

1. Make sure ONLY "szerzogabor@gmail.com" is in the contributor list for every commit

This ensures additional code review coverage and maintains code quality standards.
