"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { Project, Workspace } from "@/types";
import { cn } from "@/lib/utils";

interface SidebarProps {
  workspace: Workspace;
  projects: Project[];
  onCreateProject: () => void;
  isCollapsed: boolean;
  onToggle: () => void;
}

const ChevronLeftIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="15 18 9 12 15 6" />
  </svg>
);

const ChevronRightIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

export function Sidebar({ workspace, projects, onCreateProject, isCollapsed, onToggle }: SidebarProps) {
  const pathname = usePathname();

  // Extract active projectId from the pathname when browsing a project
  const projectMatch = pathname.match(/\/workspaces\/[^/]+\/projects\/([^/]+)/);
  const activeProjectId = projectMatch ? projectMatch[1] : null;

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !isCollapsed) {
        onToggle();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isCollapsed, onToggle]);

  return (
    <>
      {/* Mobile backdrop */}
      {!isCollapsed && (
        <div
          className="fixed inset-0 bg-black/40 z-20 md:hidden"
          onClick={onToggle}
          aria-hidden="true"
        />
      )}

      <aside
        className={cn(
          "flex flex-col bg-sidebar-bg text-sidebar-text border-r border-sidebar-border",
          "transition-all duration-300 ease-in-out",
          // Mobile: fixed overlay drawer
          "fixed inset-y-0 left-0 z-30 w-56",
          // Desktop: static in flow, width toggles
          "md:static md:z-auto md:min-h-screen",
          isCollapsed
            ? "-translate-x-full md:translate-x-0 md:w-10"
            : "translate-x-0 md:w-56"
        )}
        aria-label="Workspace navigation"
      >
        {/* Header: workspace info + toggle button */}
        <div
          className={cn(
            "border-b border-sidebar-border flex items-center flex-shrink-0",
            isCollapsed ? "md:justify-center md:p-2 px-4 py-4" : "px-4 py-4"
          )}
        >
          {!isCollapsed && (
            <div className="flex-1 min-w-0 mr-2">
              <h1 className="font-bold text-sidebar-text-active text-lg truncate">{workspace.name}</h1>
              <p className="text-xs text-sidebar-text-muted mt-0.5 truncate">{workspace.slug}</p>
            </div>
          )}
          <button
            onClick={onToggle}
            aria-label={isCollapsed ? "Show sidebar" : "Hide sidebar"}
            aria-expanded={!isCollapsed}
            className="flex-shrink-0 p-1.5 rounded text-sidebar-text-muted hover:text-sidebar-text-active hover:bg-sidebar-item-hover transition-colors"
          >
            {isCollapsed ? <ChevronRightIcon /> : <ChevronLeftIcon />}
          </button>
        </div>

        {/* Main content — hidden on desktop when collapsed */}
        <div className={cn("flex flex-col flex-1 overflow-hidden", isCollapsed && "md:hidden")}>
          <nav className="flex-1 py-4 overflow-y-auto">
            <div className="px-3 mb-2 flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-sidebar-text-muted">
                Projects
              </span>
              <button
                onClick={onCreateProject}
                className="text-sidebar-text-muted hover:text-sidebar-text-active text-lg leading-none transition-colors"
                title="New project"
              >
                +
              </button>
            </div>

            <ul className="space-y-0.5 px-2">
              {projects.map((project) => {
                const href = `/workspaces/${workspace.id}/projects/${project.id}`;
                const active = pathname.startsWith(href);
                return (
                  <li key={project.id}>
                    <Link
                      href={href}
                      className={cn(
                        "block px-3 py-2 rounded-lg text-sm truncate transition-colors",
                        active
                          ? "bg-sidebar-item-active text-white"
                          : "text-sidebar-text hover:bg-sidebar-item-hover hover:text-sidebar-text-active"
                      )}
                    >
                      {project.name}
                    </Link>
                  </li>
                );
              })}
              {projects.length === 0 && (
                <li className="px-3 py-2 text-xs text-sidebar-text-muted">No projects yet</li>
              )}
            </ul>
          </nav>

          <div className="px-3 mt-4 mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-sidebar-text-muted">
              Settings
            </span>
          </div>
          <ul className="space-y-0.5 px-2 mb-2">
            <li>
              <Link
                href={`/workspaces/${workspace.id}/agents`}
                className={cn(
                  "block px-3 py-2 rounded-lg text-sm truncate transition-colors",
                  pathname.startsWith(`/workspaces/${workspace.id}/agents`)
                    ? "bg-sidebar-item-active text-white"
                    : "text-sidebar-text hover:bg-sidebar-item-hover hover:text-sidebar-text-active"
                )}
              >
                AI Agents
              </Link>
            </li>
            {activeProjectId && (
              <>
                <li>
                  <Link
                    href={`/workspaces/${workspace.id}/projects/${activeProjectId}/settings/statuses`}
                    className={cn(
                      "block px-3 py-2 rounded-lg text-sm truncate transition-colors",
                      pathname.startsWith(`/workspaces/${workspace.id}/projects/${activeProjectId}/settings/statuses`)
                        ? "bg-sidebar-item-active text-white"
                        : "text-sidebar-text hover:bg-sidebar-item-hover hover:text-sidebar-text-active"
                    )}
                  >
                    Ticket Statuses
                  </Link>
                </li>
                <li>
                  <Link
                    href={`/workspaces/${workspace.id}/projects/${activeProjectId}/settings/automations`}
                    className={cn(
                      "block px-3 py-2 rounded-lg text-sm truncate transition-colors",
                      pathname.startsWith(`/workspaces/${workspace.id}/projects/${activeProjectId}/settings/automations`)
                        ? "bg-sidebar-item-active text-white"
                        : "text-sidebar-text hover:bg-sidebar-item-hover hover:text-sidebar-text-active"
                    )}
                  >
                    Automations
                  </Link>
                </li>
                <li>
                  <Link
                    href={`/workspaces/${workspace.id}/projects/${activeProjectId}/settings/rules`}
                    className={cn(
                      "block px-3 py-2 rounded-lg text-sm truncate transition-colors",
                      pathname.startsWith(`/workspaces/${workspace.id}/projects/${activeProjectId}/settings/rules`)
                        ? "bg-sidebar-item-active text-white"
                        : "text-sidebar-text hover:bg-sidebar-item-hover hover:text-sidebar-text-active"
                    )}
                  >
                    Ticket Rules
                  </Link>
                </li>
              </>
            )}
          </ul>

          <div className="px-3 mt-4 mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-sidebar-text-muted">
              Integrations
            </span>
          </div>
          <ul className="space-y-0.5 px-2 mb-2">
            <li>
              <Link
                href={`/workspaces/${workspace.id}/github`}
                className={cn(
                  "block px-3 py-2 rounded-lg text-sm truncate transition-colors",
                  pathname.startsWith(`/workspaces/${workspace.id}/github`)
                    ? "bg-sidebar-item-active text-white"
                    : "text-sidebar-text hover:bg-sidebar-item-hover hover:text-sidebar-text-active"
                )}
              >
                GitHub
              </Link>
            </li>
            <li>
              <Link
                href={`/workspaces/${workspace.id}/blocks`}
                className={cn(
                  "block px-3 py-2 rounded-lg text-sm truncate transition-colors",
                  pathname.startsWith(`/workspaces/${workspace.id}/blocks`)
                    ? "bg-sidebar-item-active text-white"
                    : "text-sidebar-text hover:bg-sidebar-item-hover hover:text-sidebar-text-active"
                )}
              >
                Blocks
              </Link>
            </li>
          </ul>

          <div className="px-4 py-3 border-t border-sidebar-border mt-auto">
            <Link
              href="/workspaces"
              className="text-xs text-sidebar-text-muted hover:text-sidebar-text-active transition-colors"
            >
              Switch workspace
            </Link>
          </div>
        </div>
      </aside>
    </>
  );
}
