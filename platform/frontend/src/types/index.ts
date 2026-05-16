export interface User {
  id: string;
  email: string;
  name?: string;
  createdAt: string;
}

export type WorkspaceRole = "OWNER" | "ADMIN" | "DEVELOPER" | "VIEWER";

export interface Workspace {
  id: string;
  name: string;
  slug: string;
  createdAt: string;
}

export interface WorkspaceMember {
  id: string;
  userId: string;
  email: string;
  name?: string;
  role: WorkspaceRole;
}

export interface Project {
  id: string;
  workspaceId: string;
  name: string;
  description?: string;
  createdAt: string;
}

export interface Issue {
  id: string;
  projectId: string;
  title: string;
  description?: string;
  status: IssueStatus;
  priority: IssuePriority;
  aiAssignmentState?: AIAssignmentState;
  createdAt: string;
  updatedAt: string;
}

export type IssueStatus = "open" | "in_progress" | "done" | "cancelled";
export type IssuePriority = "low" | "medium" | "high" | "urgent";
export type AIAssignmentState = "pending" | "running" | "completed" | "failed";

export interface AISession {
  id: string;
  issueId: string;
  provider: string;
  status: "pending" | "running" | "completed" | "failed";
  createdAt: string;
  updatedAt: string;
}

export interface PullRequest {
  id: string;
  issueId: string;
  githubPrNumber?: number;
  githubRepo?: string;
  title?: string;
  state?: string;
  url?: string;
  createdAt: string;
}
