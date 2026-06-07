"use client";

import { useState } from "react";
import { use } from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { CreateProjectModal } from "@/components/projects/CreateProjectModal";
import { useWorkspaces } from "@/hooks/useWorkspaces";
import { useProjects, useCreateProject } from "@/hooks/useProjects";
import { useSidebarState } from "@/hooks/useSidebarState";
import { Spinner } from "@/components/ui/Spinner";

const MenuIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <line x1="3" y1="6" x2="21" y2="6" />
    <line x1="3" y1="12" x2="21" y2="12" />
    <line x1="3" y1="18" x2="21" y2="18" />
  </svg>
);

export default function WorkspaceLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ workspaceId: string }>;
}) {
  const { workspaceId } = use(params);
  const { data: workspaces } = useWorkspaces();
  const { data: projects = [] } = useProjects(workspaceId);
  const createProject = useCreateProject(workspaceId);
  const [showCreate, setShowCreate] = useState(false);
  const { isCollapsed, toggle } = useSidebarState();

  const workspace = workspaces?.find((w) => w.id === workspaceId);

  if (!workspace) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg-app">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen">
      <Sidebar
        workspace={workspace}
        projects={projects}
        onCreateProject={() => setShowCreate(true)}
        isCollapsed={isCollapsed}
        onToggle={toggle}
      />
      <main className="flex-1 bg-bg-app min-w-0">
        {/* Mobile open button — only visible when sidebar is closed on small screens */}
        {isCollapsed && (
          <button
            onClick={toggle}
            aria-label="Show sidebar"
            className="fixed top-3 left-3 z-10 p-2 rounded-lg bg-sidebar-bg border border-sidebar-border text-sidebar-text-muted hover:text-sidebar-text-active transition-colors md:hidden"
          >
            <MenuIcon />
          </button>
        )}
        {children}
      </main>

      <CreateProjectModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSubmit={async (data) => {
          await createProject.mutateAsync(data);
          setShowCreate(false);
        }}
      />
    </div>
  );
}
