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
    <aside className="w-56 min-h-screen bg-gray-900 dark:bg-gray-950 text-gray-200 flex flex-col border-r border-gray-800">
      <div className="px-4 py-4 border-b border-gray-700 dark:border-gray-800">
        <h1 className="font-bold text-white text-lg truncate">{workspace.name}</h1>
        <p className="text-xs text-gray-400 mt-0.5 truncate">{workspace.slug}</p>
      </div>

      <nav className="flex-1 py-4 overflow-y-auto">
        <div className="px-3 mb-2 flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
            Projects
          </span>
          <button
            onClick={onCreateProject}
            className="text-gray-400 hover:text-white text-lg leading-none transition-colors"
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
                      ? "bg-indigo-600 text-white"
                      : "text-gray-300 hover:bg-gray-800 hover:text-white"
                  )}
                >
                  {project.name}
                </Link>
              </li>
            );
          })}
          {projects.length === 0 && (
            <li className="px-3 py-2 text-xs text-gray-500">No projects yet</li>
          )}
        </ul>
      </nav>

      <div className="px-4 py-3 border-t border-gray-700 dark:border-gray-800 flex items-center justify-between">
        <Link href="/workspaces" className="text-xs text-gray-400 hover:text-white transition-colors">
          Switch workspace
        </Link>
        <ThemeToggle />
      </div>
    </aside>
  );
}
