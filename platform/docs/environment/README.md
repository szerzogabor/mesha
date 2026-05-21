# Environment & Secrets Management

This document describes how environment variables and secrets are managed across all Mesha platform services.

---

## Overview

| Service | Local dev file | Production |
|---------|---------------|------------|
| Frontend | `platform/frontend/.env.local` | Vercel environment variables |
| Backend API | `platform/backend-api/.env` | Render environment variables |
| Backend Worker | `platform/backend-worker/.env` | Render environment variables |
| Docker full-stack | `platform/infrastructure/.env` | N/A (local only) |

---

## Secret Classification

Every environment variable belongs to one of four categories.

| Prefix | Meaning | Examples |
|--------|---------|---------|
| `PUBLIC_` | Safe to expose in the browser | `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_SENTRY_DSN` |
| `SERVER_` | Server-side only; must never reach the client | `CLERK_SECRET_KEY`, `SENTRY_AUTH_TOKEN` |
| `INTERNAL_` | Backend credentials; never exposed outside the service | `DB_PASSWORD`, `REDIS_URL`, `BLOCKS_API_KEY` |
| `WEBHOOK_` | Secrets used to validate incoming webhook payloads | `GITHUB_WEBHOOK_SECRET` |

> **Rule:** If a variable name does not start with `NEXT_PUBLIC_`, treat it as `SERVER_` or `INTERNAL_` by default.

---

## Local Development Setup

### Prerequisites

- Docker Desktop (for running PostgreSQL + Redis)
- Java 21 (for backend services)
- Node.js 22 (for the frontend)

### Step 1 — Start infrastructure

```bash
cd platform/infrastructure
docker compose -f docker-compose.dev.yml up -d
```

This starts PostgreSQL on `5432` and Redis on `6379`.

### Step 2 — Configure backend-api

```bash
cp platform/backend-api/.env.example platform/backend-api/.env
```

Edit `platform/backend-api/.env` and set:

- `CLERK_JWKS_URI` — from [Clerk dashboard](https://dashboard.clerk.com) → your app → API Keys → JWKS URL

The database and Redis values default to the Docker Compose service so no changes are needed for local dev.

### Step 3 — Configure backend-worker

```bash
cp platform/backend-worker/.env.example platform/backend-worker/.env
```

Edit `platform/backend-worker/.env` and set:

- `BLOCKS_API_KEY` — from the Blocks dashboard
- `GITHUB_APP_ID` — from GitHub → Settings → Developer settings → GitHub Apps → your app
- `GITHUB_APP_PRIVATE_KEY` — download the `.pem` from the GitHub App page, then flatten to one line:
  ```bash
  awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}' path/to/private-key.pem
  ```
- `GITHUB_WEBHOOK_SECRET` — the secret you configured in GitHub App settings → Webhook secret

### Step 4 — Configure frontend

```bash
cp platform/frontend/.env.example platform/frontend/.env.local
```

Edit `platform/frontend/.env.local` and set:

- `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` — from [Clerk dashboard](https://dashboard.clerk.com) → API Keys
- `CLERK_SECRET_KEY` — from the same page (keep this secret)
- `NEXT_PUBLIC_SENTRY_DSN` — from Sentry → your project → Client Keys (optional for local dev)

### Step 5 — Run services

Backend API (from `platform/backend-api/`):
```bash
./mvnw spring-boot:run
```

Backend Worker (from `platform/backend-worker/`):
```bash
./mvnw spring-boot:run
```

Frontend (from `platform/frontend/`):
```bash
npm install
npm run dev
```

---

## Full Docker Compose Stack

To run all services in Docker (useful for integration testing):

```bash
cd platform/infrastructure
cp .env.example .env
# Fill in BLOCKS_API_KEY, GITHUB_APP_ID, GITHUB_APP_PRIVATE_KEY, GITHUB_WEBHOOK_SECRET
docker compose -f docker-compose.yml up --build
```

Services will be available at:

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Backend Worker: http://localhost:8081

---

## Vercel Configuration

Configure these in the Vercel dashboard under **Project → Settings → Environment Variables**.

Apply each variable to the correct environment (Production / Preview / Development):

| Variable | Production | Preview | Development |
|----------|-----------|---------|-------------|
| `NEXT_PUBLIC_API_URL` | `https://mesha-backend-api.onrender.com` | Preview backend URL | `http://localhost:8080` |
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Production key | Test key | Test key |
| `CLERK_SECRET_KEY` | Production secret | Test secret | Test secret |
| `NEXT_PUBLIC_SENTRY_DSN` | Production DSN | Preview DSN | (optional) |
| `NEXT_PUBLIC_ENVIRONMENT` | `production` | `preview` | `local` |
| `NEXT_PUBLIC_APP_VERSION` | Git tag | Git SHA | (optional) |
| `SENTRY_AUTH_TOKEN` | Auth token | Auth token | (optional) |

---

## Render Configuration

Infrastructure is defined in `render.yaml` at the repository root. Render auto-injects database and Redis credentials from the managed services (`mesha-db`, `mesha-redis`).

The following variables must be set manually in the Render dashboard under each service's **Environment** tab because they are marked `sync: false` in `render.yaml`:

**mesha-backend-api:**
- `CORS_ALLOWED_ORIGINS` — your Vercel frontend URL (e.g. `https://mesha.vercel.app`)
- `CLERK_JWKS_URI` — from Clerk dashboard → API Keys → JWKS URL

**mesha-backend-worker:**
- `BLOCKS_API_URL`
- `BLOCKS_API_KEY`
- `GITHUB_APP_ID`
- `GITHUB_APP_PRIVATE_KEY`
- `GITHUB_WEBHOOK_SECRET`

---

## Environment Isolation

| Environment | Frontend | Backend API | Backend Worker |
|-------------|----------|-------------|----------------|
| Local | `.env.local` | `.env` | `.env` |
| Preview | Vercel preview env vars | Render preview (manual) | Render preview (manual) |
| Staging | Vercel staging env vars | Render staging | Render staging |
| Production | Vercel production env vars | Render production | Render production |

Use separate Clerk applications (or Clerk environments) for production vs. non-production to prevent test data leaking into production auth.

---

## Security Requirements

- **No secrets in the repository.** The `.gitignore` excludes all `.env` and `.env.*` files except `.env.example` files.
- **Webhook payloads must be validated.** The backend-worker verifies every GitHub webhook using HMAC-SHA256 with `GITHUB_WEBHOOK_SECRET`.
- **Principle of least exposure.** Secrets are scoped to the service that needs them. The frontend never receives `INTERNAL_` or `WEBHOOK_` variables.
- **Rotate secrets immediately** if they are accidentally committed or exposed. See [Secret Rotation](#secret-rotation) below.

---

## Secret Rotation

Rotate a secret by following these steps:

1. **Generate a new secret** using a cryptographically secure generator:
   ```bash
   openssl rand -hex 32
   ```

2. **Update the secret in all environments** (Vercel / Render dashboard) before invalidating the old one.

3. **Redeploy** affected services to pick up the new value.

4. **Invalidate the old secret** only after confirming all services have restarted successfully.

### Per-secret rotation guides

| Secret | How to rotate |
|--------|--------------|
| `CLERK_SECRET_KEY` | Clerk dashboard → API Keys → Roll secret key |
| `GITHUB_APP_PRIVATE_KEY` | GitHub App page → Generate a new private key; delete the old key after rotation |
| `GITHUB_WEBHOOK_SECRET` | Update in GitHub App settings and in Render simultaneously; brief validation failures are expected during rotation |
| `BLOCKS_API_KEY` | Blocks dashboard → regenerate API key |
| `DB_PASSWORD` | Render dashboard → database → rotate credentials; update backend services |
| `SENTRY_AUTH_TOKEN` | Sentry → Account settings → API auth tokens → revoke old, create new |

---

## Adding a New Secret

1. Choose the correct classification prefix (`PUBLIC_`, `SERVER_`, `INTERNAL_`, `WEBHOOK_`).
2. Add it to the relevant `.env.example` file(s) with a comment describing where to obtain it.
3. Add it to the `render.yaml` `envVars` block (with `sync: false` for manually managed secrets).
4. Add it to the Vercel environment variables if it is a frontend variable.
5. Update this document.
