"use client";

import { use, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useProjects } from "@/hooks/useProjects";
import { Spinner } from "@/components/ui/Spinner";

export default function WorkspacePage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  const { workspaceId } = use(params);
  const router = useRouter();
  const { data: projects, isLoading } = useProjects(workspaceId);

  useEffect(() => {
    if (projects && projects.length > 0) {
      router.replace(`/workspaces/${workspaceId}/projects/${projects[0].id}`);
    }
  }, [projects, workspaceId, router]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full py-32">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  if (projects && projects.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full py-32 text-text-tertiary">
        <p className="text-lg mb-1">No projects yet</p>
        <p className="text-sm">Create a project using the sidebar.</p>
      </div>
    );
  }

  return null;
}
