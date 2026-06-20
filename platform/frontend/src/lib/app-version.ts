/**
 * Single source of truth for the user-facing app version and release notes,
 * surfaced on the /download install page. Bump `APP_VERSION` and prepend a new
 * entry to `RELEASE_NOTES` with each user-visible release.
 */
export const APP_VERSION = "0.1.0";

export interface ReleaseNote {
  version: string;
  date: string;
  highlights: string[];
}

export const RELEASE_NOTES: ReleaseNote[] = [
  {
    version: "0.1.0",
    date: "2026-06-20",
    highlights: [
      "Installable Android PWA with offline app shell and home-screen support",
      "Android-style bottom navigation and mobile dashboard",
      "Mobile-optimized issue, session, and agent experiences",
      "Tap-to-move Kanban interaction for phones",
    ],
  },
];
