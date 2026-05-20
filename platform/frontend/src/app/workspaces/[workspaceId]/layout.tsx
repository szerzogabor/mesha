"use client";

import { useState } from "react";
import { use } from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { CreateProjectModal } from "@/components/projects/CreateProjectModal";
import { useWorkspaces } from "@/hooks/useWorkspaces";
import { useProjects, useCreateProject } from "@/hooks/useProjects";
import { Spinner } from "@/components/ui/Spinner";

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

  const workspace = workspaces?.find((w) => w.id === workspaceId);

  if (!workspace) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-950">
        <Spinner size="lg" className="text-indigo-600" />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen">
      <Sidebar
        workspace={workspace}
        projects={projects}
        onCreateProject={() => setShowCreate(true)}
      />
      <main className="flex-1 bg-gray-50 dark:bg-gray-950">{children}</main>

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
