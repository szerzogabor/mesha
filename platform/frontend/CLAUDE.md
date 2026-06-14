# Frontend — Agent Guide

Next.js 15 (TypeScript) web app. Mobile-first. Uses Clerk for auth, TanStack Query for server state, Zustand for client state. See root `CLAUDE.md` for git/PR rules.

---

## Running Locally

```bash
npm install
npm run dev          # Dev server at http://localhost:3000
npm run build        # Production build
npm run lint         # ESLint
npm run type-check   # TypeScript compiler check (no emit)
npm run test:e2e     # Playwright end-to-end tests
```

Backend must be running at `http://localhost:8080` (set via `NEXT_PUBLIC_API_URL` in `.env.local`).

Copy `.env.example` → `.env.local` and fill in values before starting.

---

## Directory Structure

```
src/
├── app/                           # Next.js App Router (file-based routing)
│   ├── layout.tsx                 # Root layout — Clerk provider, theme script
│   ├── providers.tsx              # TanStack Query + Zustand store init
│   └── workspaces/[workspaceId]/
│       ├── page.tsx               # Workspace home
│       ├── projects/[projectId]/
│       │   ├── page.tsx           # Issue list / Kanban
│       │   └── issues/[issueId]/  # Issue detail
│       ├── blocks/                # Blocks config UI
│       └── github/                # GitHub integration UI
├── components/                    # Reusable React components
│   ├── ui/                        # Generic UI primitives (buttons, modals, etc.)
│   ├── issues/                    # Issue-related components
│   ├── projects/                  # Project-related components
│   ├── blocks/                    # Blocks session UI
│   ├── comments/                  # Comment thread UI
│   ├── github/                    # GitHub PR UI
│   ├── automation/                # Automation rule config UI
│   ├── activity/                  # Activity feed
│   ├── settings/                  # Settings pages
│   └── layout/                    # Nav, sidebar, header
├── hooks/                         # Custom React hooks (85+ files)
│   ├── useIssues.ts               # Issue CRUD + query invalidation
│   ├── useBlocksSessions.ts       # Session management
│   ├── useAIDraft.ts              # Draft generation flow
│   ├── useWorkspaces.ts           # Workspace/member data
│   └── use*.ts                    # One hook file per domain area
├── types/index.ts                 # ALL shared TypeScript interfaces — check here first
└── lib/
    ├── api-client.ts              # REST wrapper — auth headers, error handling, correlation IDs
    ├── logger.ts                  # Structured client-side logger
    └── otel/                      # OpenTelemetry SDK config
```

---

## Data Fetching Patterns

All server state goes through **TanStack Query** hooks in `hooks/`. Never call the API client directly from components.

### Reading data
```typescript
// In hooks/useIssues.ts
export function useIssues(projectId: string) {
  return useQuery({
    queryKey: ['issues', projectId],
    queryFn: () => apiClient.get(`/api/projects/${projectId}/issues`),
  });
}

// In component
const { data: issues, isLoading } = useIssues(projectId);
```

### Mutating data
```typescript
// In hooks/useIssues.ts
export function useUpdateIssue() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => apiClient.patch(`/api/issues/${payload.id}`, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['issues', variables.projectId] });
    },
  });
}
```

**Rules:**
- Query keys must be consistent — use arrays like `['issues', projectId]`
- Invalidate related queries after mutations
- Never store API-fetched data in Zustand (that's TanStack Query's job)

---

## Auth

Clerk is the auth provider. The `api-client.ts` automatically attaches the Clerk JWT to every request:

```typescript
const token = await auth.getToken();
headers['Authorization'] = `Bearer ${token}`;
```

Components can use `useUser()`, `useAuth()` from `@clerk/nextjs`. The root layout wraps everything in `<ClerkProvider>`.

---

## Client State (Zustand)

Zustand stores are initialized in `app/providers.tsx`. Use Zustand only for:
- UI state (sidebar open/closed, active modal)
- Optimistic UI state that doesn't belong in the server cache

Do NOT store fetched data in Zustand — use TanStack Query for that.

---

## TypeScript Types

All shared types live in `src/types/index.ts`. **Check here before defining new interfaces.**

Key types:
- `Issue`, `IssueStatus`, `IssuePriority`
- `BlocksSession`, `AIExecutionState`
- `Project`, `Workspace`, `WorkspaceMember`
- `GitHubPullRequest`, `GitHubRepository`
- `Comment`, `ActivityEvent`
- `AIDraft`, `AutomationRule`, `TicketRule`
- `Label`, `IssueLink`

---

## API Client

`src/lib/api-client.ts` is the single entry point for all HTTP calls.

- Attaches `Authorization: Bearer <token>`
- Injects `X-Correlation-ID` header for tracing
- Throws typed errors on non-2xx responses
- Base URL from `NEXT_PUBLIC_API_URL`

---

## Adding a New Feature — Checklist

1. **Types** — add/extend interfaces in `src/types/index.ts`
2. **Hook** — add a hook file in `src/hooks/use{Feature}.ts` using TanStack Query
3. **Component** — add component(s) in `src/components/{domain}/`
4. **Route** — add page in `src/app/workspaces/[workspaceId]/...` if needed

---

## Styling

- **TailwindCSS** — utility classes only; no custom CSS files unless unavoidable
- `tailwind.config.ts` defines the design tokens (colors, fonts, spacing)
- Mobile-first: default styles are mobile, use `md:` / `lg:` for wider breakpoints
- Dark mode supported (class-based)

---

## Testing

E2E tests with Playwright: `e2e/tests/`

| Test File | Coverage |
|-----------|---------|
| `smoke.spec.ts` | App loads, no JS errors |
| `auth.spec.ts` | Clerk login/logout |
| `navigation.spec.ts` | Route navigation |
| `issue-management.spec.ts` | Create, edit, delete issues |
| `kanban.spec.ts` | Drag-and-drop board |
| `mobile.spec.ts` | Mobile viewport responsiveness |
| `github-integration.spec.ts` | GitHub OAuth + repo sync |

Run: `npm run test:e2e`
Config: `playwright.config.ts`

---

## Observability

- OpenTelemetry SDK initialized in `instrumentation-client.ts`
- Browser traces sent to Grafana Cloud (via `NEXT_PUBLIC_OTEL_EXPORTER_OTLP_ENDPOINT`)
- Use `src/lib/logger.ts` for structured client-side logging (not `console.log`)

---

## Environment Variables

Copy `.env.example` to `.env.local`:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY=pk_test_...
CLERK_SECRET_KEY=sk_test_...
NEXT_PUBLIC_OTEL_EXPORTER_OTLP_ENDPOINT=https://...
NEXT_PUBLIC_OTEL_EXPORTER_OTLP_AUTH=<base64>
```

`NEXT_PUBLIC_*` variables are exposed to the browser. Never put secrets in `NEXT_PUBLIC_*` vars.
