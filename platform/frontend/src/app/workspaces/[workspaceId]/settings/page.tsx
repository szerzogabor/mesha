"use client";

import { use } from "react";
import Link from "next/link";
import { useProjects } from "@/hooks/useProjects";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { InstallAppButton } from "@/components/pwa/InstallAppButton";

const ChevronRight = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="text-text-tertiary">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

function Row({ href, label, description }: { href: string; label: string; description?: string }) {
  return (
    <Link
      href={href}
      className="flex items-center justify-between gap-3 px-4 py-3.5 hover:bg-bg-surface-hover transition-colors min-h-[52px]"
    >
      <div className="min-w-0">
        <p className="text-sm font-medium text-text-primary">{label}</p>
        {description && (
          <p className="text-xs text-text-tertiary truncate">{description}</p>
        )}
      </div>
      <ChevronRight />
    </Link>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="px-1 mb-1.5 text-xs font-semibold uppercase tracking-wider text-text-tertiary">
        {title}
      </h2>
      <div className="bg-bg-surface border border-border-default rounded-xl overflow-hidden divide-y divide-border-default">
        {children}
      </div>
    </section>
  );
}

/**
 * Mobile settings hub — the destination for the bottom-nav "Settings" tab.
 * Consolidates the desktop sidebar's settings + integration links into a
 * touch-friendly list. Every row points at an existing page; no duplicated
 * settings logic lives here.
 */
export default function WorkspaceSettingsPage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  const { workspaceId } = use(params);
  const { data: projects = [] } = useProjects(workspaceId);
  const base = `/workspaces/${workspaceId}`;
  const projectId = projects[0]?.id;

  return (
    <div className="max-w-2xl mx-auto px-4 md:px-6 py-5 md:py-8 space-y-6">
      <h1 className="text-xl md:text-2xl font-semibold text-text-primary">
        Settings
      </h1>

      <Group title="Agents">
        <Row href={`${base}/agents`} label="AI Agents" description="Configure Blocks agents" />
        <Row href={`${base}/connector-agents`} label="Connector Agents" description="Local executor machines" />
        <Row href={`${base}/agent-sessions`} label="Agent Sessions" description="Running & past sessions" />
      </Group>

      {projectId && (
        <Group title="Project">
          <Row href={`${base}/projects/${projectId}/settings/statuses`} label="Ticket Statuses" />
          <Row href={`${base}/projects/${projectId}/settings/automations`} label="Automations" />
          <Row href={`${base}/projects/${projectId}/settings/rules`} label="Ticket Rules" />
        </Group>
      )}

      <Group title="Integrations">
        <Row href={`${base}/github`} label="GitHub" description="Repositories & pull requests" />
        <Row href={`${base}/blocks`} label="Blocks" description="AI provider connection" />
      </Group>

      <Group title="App">
        <div className="flex items-center justify-between gap-3 px-4 py-3.5 min-h-[52px]">
          <div>
            <p className="text-sm font-medium text-text-primary">Appearance</p>
            <p className="text-xs text-text-tertiary">Light, dark, or system</p>
          </div>
          <ThemeToggle />
        </div>
        <div className="flex items-center justify-between gap-3 px-4 py-3.5">
          <div className="min-w-0">
            <p className="text-sm font-medium text-text-primary">Install Mesha</p>
            <p className="text-xs text-text-tertiary">Add to your home screen</p>
          </div>
          <InstallAppButton label="Install" className="shrink-0 px-3 py-2 text-xs" />
        </div>
        <Row href="/workspaces" label="Switch workspace" />
      </Group>
    </div>
  );
}
