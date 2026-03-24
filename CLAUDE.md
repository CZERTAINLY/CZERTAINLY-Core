# CZERTAINLY — AI Agent Guidelines

This file provides instructions for Claude, GitHub Copilot, and other AI/agentic tools working in this repository.

## Commit Message Format

Follow the [CZERTAINLY Contributing Guide](https://github.com/CZERTAINLY/CZERTAINLY/blob/develop/CONTRIBUTING.md).

### Rules

- Write the summary in the **imperative mood**: "Fix bug" not "Fixed bug" or "Fixes bug"
- **Capitalize** the first word of the summary
- Keep the summary **50 characters or less**
- Leave the **second line blank** (blank line between summary and body)
- Wrap body lines at **72 characters**
- Always include a **link to the GitHub issue** at the end
- Every commit must be linked to an issue — **Always Link Commits**
- Only commit working, tested code — **Test Before You Commit**
- Commit related changes together — do not mix unrelated fixes in one commit

### Template

```
Short imperative summary (50 chars or less)

Optional longer explanation of what and why, wrapped at
72 characters. Leave the line above blank.

Further paragraphs or bullet points if needed:
 - Use a hyphen or asterisk for bullets
 - Indent bullets by two spaces and add a space after the hyphen

Link: https://github.com/CZERTAINLY/<repo>/issues/<number>
```

### Example

```
Fix CBOM client not reconfigured after URL change

The old event-based WebClient caching required explicit refresh
events to pick up URL changes from platform settings. Replace with
a per-request client creation that reads from SettingsCache directly,
eliminating the stale-client problem entirely.

Link: https://github.com/CZERTAINLY/CZERTAINLY-Core/issues/1330
```

## General Development Rules

- Run tests before committing
- Do not commit unrelated files (build artifacts, IDE config, local overrides such as `application.yml` changes)
- Use feature branches following the git-flow model (`feat/`, `fix/`, `hotfix/`)
- Branch names should reference the issue number where applicable

## Co-authorship

When an AI assistant (Claude, Copilot, etc.) contributes to a commit, add a
`Co-Authored-By` trailer to the commit message:

```
Fix short summary here

Optional body text.

Link: https://github.com/CZERTAINLY/CZERTAINLY-Core/issues/<number>
Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

Use the exact model name and the `noreply@anthropic.com` address so GitHub
renders the co-authorship badge correctly.
