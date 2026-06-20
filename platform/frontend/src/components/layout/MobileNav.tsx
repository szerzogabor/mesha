"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { useProjects } from "@/hooks/useProjects";
import { cn } from "@/lib/utils";

const LAST_PROJECT_KEY = "mesha-last-project";

const HomeIcon = ({ active }: { active: boolean }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill={active ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M3 9.5 12 3l9 6.5" fill="none" />
    <path d="M5 10v10h14V10" />
  </svg>
);
const IssuesIcon = ({ active }: { active: boolean }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill={active ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="3" y="4" width="18" height="16" rx="2" fill={active ? "currentColor" : "none"} />
    <path d="M7 9h10M7 13h6" stroke={active ? "var(--bg-surface)" : "currentColor"} />
  </svg>
);
const SessionsIcon = ({ active }: { active: boolean }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="9" fill={active ? "currentColor" : "none"} />
    <path d="M12 7v5l3 2" stroke={active ? "var(--bg-surface)" : "currentColor"} />
  </svg>
);
const ProjectsIcon = ({ active }: { active: boolean }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" fill={active ? "currentColor" : "none"} />
  </svg>
);
const SettingsIcon = ({ active }: { active: boolean }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill={active ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="3" fill="none" stroke={active ? "var(--bg-surface)" : "currentColor"} />
    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" fill="none" />
  </svg>
);

interface MobileNavProps {
  workspaceId: string;
}

/**
 * Persistent Android-style bottom tab bar. Mobile only (`md:hidden`); desktop
 * keeps the sidebar untouched. Tabs route into existing pages — no duplicated
 * page logic — using the active (or most recently visited) project for the
 * project-scoped tabs.
 */
export function MobileNav({ workspaceId }: MobileNavProps) {
  const pathname = usePathname();
  const { data: projects = [] } = useProjects(workspaceId);

  // Resolve a project for the project-scoped tabs: current route → remembered → first.
  const match = pathname.match(/\/projects\/([^/]+)/);
  const currentProjectId = match ? match[1] : null;
  const [rememberedProjectId, setRememberedProjectId] = useState<string | null>(null);

  useEffect(() => {
    if (currentProjectId) {
      setRememberedProjectId(currentProjectId);
      localStorage.setItem(LAST_PROJECT_KEY, currentProjectId);
    } else {
      const stored = localStorage.getItem(LAST_PROJECT_KEY);
      if (stored) setRememberedProjectId(stored);
    }
  }, [currentProjectId]);

  const projectId =
    currentProjectId ||
    rememberedProjectId ||
    (projects.length > 0 ? projects[0].id : null);

  const base = `/workspaces/${workspaceId}`;
  const issuesHref = projectId ? `${base}/projects/${projectId}` : `${base}/projects`;

  const tabs = [
    {
      key: "home",
      label: "Home",
      href: base,
      Icon: HomeIcon,
      active: pathname === base,
    },
    {
      key: "issues",
      label: "Issues",
      href: issuesHref,
      Icon: IssuesIcon,
      active:
        /\/projects\/[^/]+/.test(pathname) && !pathname.includes("/settings"),
    },
    {
      key: "sessions",
      label: "Sessions",
      href: `${base}/agent-sessions`,
      Icon: SessionsIcon,
      active: pathname.includes("/agent-sessions") || pathname.includes("/blocks"),
    },
    {
      key: "projects",
      label: "Projects",
      href: `${base}/projects`,
      Icon: ProjectsIcon,
      active: pathname === `${base}/projects`,
    },
    {
      key: "settings",
      label: "Settings",
      href: `${base}/settings`,
      Icon: SettingsIcon,
      active:
        pathname.startsWith(`${base}/settings`) ||
        pathname.includes("/agents") ||
        pathname.includes("/github"),
    },
  ];

  return (
    <nav
      aria-label="Primary"
      className="fixed bottom-0 inset-x-0 z-30 md:hidden bg-bg-surface/95 backdrop-blur border-t border-border-default pb-safe"
    >
      <ul className="flex items-stretch justify-around h-14">
        {tabs.map(({ key, label, href, Icon, active }) => (
          <li key={key} className="flex-1">
            <Link
              href={href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "h-full flex flex-col items-center justify-center gap-0.5 text-[10px] font-medium transition-colors",
                active
                  ? "text-accent"
                  : "text-text-tertiary hover:text-text-secondary"
              )}
            >
              <Icon active={active} />
              <span>{label}</span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
