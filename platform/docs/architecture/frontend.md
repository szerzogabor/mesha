# Frontend Architecture

## 1. Platform

**Hosting:** Vercel
**Framework:** Next.js 15+

Responsibilities:
- Next.js application hosting
- Static assets + SSR/ISR
- Edge caching
- Authentication flows

---

## 2. Technology Stack

| Layer | Technology |
|---|---|
| Framework | Next.js 15+ |
| Language | TypeScript |
| Styling | TailwindCSS |
| Component Library | shadcn/ui |
| Server State | TanStack Query |
| Client State | Zustand |

---

## 3. Mobile-First UX

### Required Mobile Support
- Ticket creation
- AI draft review
- Approve/reject AI tasks
- PR viewing and commenting
- Notifications
- Session monitoring

### Mobile Design Principles
- Single-column layouts
- Bottom navigation
- Fast PR summaries
- AI activity feed
- Swipe actions

### Installable PWA (Android-first)
Mesha ships as an installable Progressive Web App built into this same Next.js
frontend — **not** a separate app, React Native, or Flutter project:
- Web App Manifest (`src/app/manifest.ts`), maskable icons, splash/theme colors.
- Service worker (`public/sw.js`) with an offline app shell + `offline.html`.
- Android-style bottom navigation (`MobileNav`), workspace dashboard, projects
  index, and a mobile settings hub.
- Touch-friendly Kanban (tap-to-move bottom sheet) alongside desktop drag-and-drop.
- Install/download experience at `/` (hero CTA) and `/download` (steps, QR, version).
- Push-notification **foundation** (`src/lib/notifications/*`) — abstraction only.

Full details: [`platform/docs/MOBILE.md`](../MOBILE.md).

---

## 4. Realtime Updates

**Mechanism:** WebSockets (connected to `backend-api`)

Events:
- AI started working
- PR created
- Review requested
- Session completed

---

## 5. Key Pages / Routes

| Route | Description |
|---|---|
| `/` | Dashboard / ticket feed |
| `/tickets/new` | Create ticket (natural language prompt) |
| `/tickets/[id]` | Ticket detail with AI draft review |
| `/tickets/[id]/pr` | PR review view |
| `/sessions` | AI session monitoring |
| `/settings` | Workspace settings, GitHub connection |

---

## 6. AI Draft Review Flow (UI)

```
1. User writes prompt → POST /api/ai/ticket-drafts
2. Draft displayed for review
3. User edits title / acceptance criteria / scope
4. User approves → ticket created
5. User can assign to Blocks from ticket detail
```
