import { GitHubInstallation } from "@/types";

export function isActiveGitHubInstallation(installation: GitHubInstallation): boolean {
  return installation.status === "active";
}

export function isUninstalledGitHubInstallation(installation: GitHubInstallation): boolean {
  return installation.status === "uninstalled" || installation.status === "deleted";
}

export function githubAppInstallUrl(workspaceId: string): string {
  const appName = process.env.NEXT_PUBLIC_GITHUB_APP_NAME ?? "mesha-github-app";
  return `https://github.com/apps/${appName}/installations/new?state=${workspaceId}`;
}
