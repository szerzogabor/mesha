"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useProjects, useCreateProject } from "@/hooks/useProjects";
import { CreateProjectModal } from "@/components/projects/CreateProjectModal";
import { Spinner } from "@/components/ui/Spinner";

/**
 * Projects index — the destination for the mobile "Projects" tab and the
 * dashboard "View all" link. Lists every project as a touch-friendly card and
 * reuses the existing CreateProjectModal so there is no duplicated create flow.
 */
export default function ProjectsIndexPage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  const { workspaceId } = use(params);
  const { data: projects = [], isLoading } = useProjects(workspaceId);
  const createProject = useCreateProject(workspaceId);
  const [showCreate, setShowCreate] = useState(false);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-32">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 md:px-6 py-5 md:py-8">
      <div className="flex items-center justify-between mb-5 gap-3">
        <h1 className="text-xl md:text-2xl font-semibold text-text-primary">
          Projects
        </h1>
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover transition-colors min-h-[44px]"
        >
          + New
        </button>
      </div>

      {projects.length === 0 ? (
        <div className="text-center py-16 text-text-tertiary">
          <p className="text-lg mb-1">No projects yet</p>
          <p className="text-sm">Create your first project to get started.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {projects.map((project) => (
            <Link
              key={project.id}
              href={`/workspaces/${workspaceId}/projects/${project.id}`}
              className="block bg-bg-surface border border-border-default rounded-xl p-4 hover:border-accent/40 hover:shadow-sm transition-all"
            >
              <p className="font-medium text-text-primary truncate">
                {project.name}
              </p>
              {project.description ? (
                <p className="text-sm text-text-tertiary mt-1 line-clamp-2">
                  {project.description}
                </p>
              ) : (
                <p className="text-sm text-text-tertiary mt-1">No description</p>
              )}
            </Link>
          ))}
        </div>
      )}

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
