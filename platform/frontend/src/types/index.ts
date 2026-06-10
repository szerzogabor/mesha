export interface Workspace {
  id: string;
  name: string;
  slug: string;
  createdAt: string;
}

export interface Project {
  id: string;
  workspaceId: string;
  name: string;
  description?: string;
  key?: string;
  createdAt: string;
  updatedAt: string;
}

export type IssueStatus = "BACKLOG" | "TODO" | "IN_PROGRESS" | "REVIEW" | "DONE";
export type IssuePriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type AIAssignmentState = "pending" | "running" | "completed" | "failed";
export type AIExecutionState =
  | "CREATED"
  | "PLANNING"
  | "EXECUTING"
  | "WAITING_REVIEW"
  | "PR_OPENED"
  | "DONE"
  | "FAILED"
  | "CANCELED";
export type ActivityEventType =
  | "ISSUE_CREATED"
  | "STATUS_CHANGED"
  | "PRIORITY_CHANGED"
  | "ASSIGNEE_CHANGED"
  | "LABEL_ADDED"
  | "LABEL_REMOVED"
  | "COMMENT_ADDED"
  | "TITLE_CHANGED"
  | "DESCRIPTION_CHANGED"
  | "ISSUE_CREATED_FROM_AI_DRAFT"
  | "AI_ASSIGNED"
  | "AI_STATE_CHANGED"
  | "AI_PR_OPENED"
  | "AI_COMPLETED"
  | "AI_FAILED"
  | "AI_CANCELED";

export type AIDraftStatus = "PENDING" | "COMPLETED" | "FAILED" | "APPROVED" | "REJECTED";

export interface AIDraft {
  id: string;
  projectId: string;
  prompt: string;
  status: AIDraftStatus;
  generatedTitle?: string;
  generatedDescription?: string;
  acceptanceCriteria?: string;
  suggestedLabels?: string;
  prioritySuggestion?: string;
  implementationNotes?: string;
  scopeNotes?: string;
  outOfScopeNotes?: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserSummary {
  id: string;
  email: string;
  name?: string;
  createdAt: string;
}

export interface Label {
  id: string;
  workspaceId: string;
  name: string;
  color: string;
}

export interface Issue {
  id: string;
  projectId: string;
  identifier?: string;
  title: string;
  description?: string;
  status: IssueStatus;
  priority: IssuePriority;
  assignee?: UserSummary;
  labels: Label[];
  aiAssignmentState?: AIAssignmentState;
  createdAt: string;
  updatedAt: string;
}

export interface Comment {
  id: string;
  issueId: string;
  body: string;
  author?: UserSummary;
  parentId?: string;
  replies: Comment[];
  createdAt: string;
  updatedAt: string;
}

export interface ActivityEvent {
  id: string;
  issueId: string;
  user?: UserSummary;
  eventType: ActivityEventType;
  oldValue?: string;
  newValue?: string;
  createdAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface AISession {
  id: string;
  issueId: string;
  provider: string;
  status: "pending" | "running" | "completed" | "failed";
  createdAt: string;
  updatedAt: string;
}

export type MessageRole = "USER" | "AI" | "SYSTEM";

export interface LinkedPullRequest {
  id: string;
  githubPrNumber?: number;
  title?: string;
  state?: string;
  sourceBranch?: string;
  targetBranch?: string;
  htmlUrl: string;
  draft?: boolean;
  mergedAt?: string;
  checksStatus?: string;
  authorLogin?: string;
}

export interface BlocksSession {
  id: string;
  issueId: string;
  provider: string;
  providerSessionId?: string;
  executionState: AIExecutionState;
  retryCount: number;
  prUrl?: string;
  prNumber?: number;
  branchName?: string;
  errorMessage?: string;
  sessionUrl?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
  linkedPullRequest?: LinkedPullRequest;
}

export interface BlocksMessage {
  id: string;
  sessionId: string;
  message: string;
  role: MessageRole;
  createdAt: string;
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

export interface GitHubInstallation {
  id: string;
  installationId: number;
  appId: number;
  accountLogin: string;
  accountType: string;
  accountAvatarUrl?: string;
  status: string;
  manageUrl?: string;
  lastRefreshAt?: string;
  createdAt: string;
}

export interface AvailableRepository {
  id: number;
  name: string;
  fullName: string;
  owner: string;
  isPrivate: boolean;
  defaultBranch: string;
  description?: string;
  htmlUrl: string;
}

export interface GitHubRepository {
  id: string;
  workspaceId: string;
  githubRepoId: number;
  owner: string;
  name: string;
  fullName: string;
  isPrivate: boolean;
  defaultBranch: string;
  description?: string;
  htmlUrl: string;
  connected: boolean;
  lastSyncedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface BlocksConfig {
  id: string;
  workspaceId: string;
  blocksWorkspaceId?: string;
  status: string;
  connectedAt: string;
  updatedAt: string;
}

export interface GitHubPullRequest {
  id: string;
  repositoryId: string;
  githubPrNumber: number;
  title: string;
  body?: string;
  state: "open" | "closed";
  authorLogin?: string;
  authorAvatarUrl?: string;
  sourceBranch: string;
  targetBranch: string;
  htmlUrl: string;
  draft: boolean;
  commitsCount: number;
  reviewState?: string;
  checksStatus?: string;
  mergedAt?: string;
  closedAt?: string;
  createdAt: string;
  updatedAt: string;
}
