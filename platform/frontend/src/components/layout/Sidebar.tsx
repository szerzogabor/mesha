"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Project, Workspace } from "@/types";
import { cn } from "@/lib/utils";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

interface SidebarProps {
  workspace: Workspace;
  projects: Project[];
  onCreateProject: () => void;
}

export function Sidebar({ workspace, projects, onCreateProject }: SidebarProps) {
  const pathname = usePathname();

  return (
    <aside className="w-56 min-h-screen bg-sidebar-bg text-sidebar-text flex flex-col border-r border-sidebar-border">
      <div className="px-4 py-4 border-b border-sidebar-border">
        <h1 className="font-bold text-sidebar-text-active text-lg truncate">{workspace.name}</h1>
        <p className="text-xs text-sidebar-text-muted mt-0.5 truncate">{workspace.slug}</p>
      </div>

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

      <div className="px-4 py-3 border-t border-sidebar-border mt-auto flex items-center justify-between">
        <Link href="/workspaces" className="text-xs text-sidebar-text-muted hover:text-sidebar-text-active transition-colors">
          Switch workspace
        </Link>
        <ThemeToggle />
      </div>
    </aside>
  );
}
