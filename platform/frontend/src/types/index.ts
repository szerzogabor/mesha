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
  createdAt: string;
  updatedAt: string;
}

export type IssueStatus = "BACKLOG" | "TODO" | "IN_PROGRESS" | "REVIEW" | "DONE";
export type IssuePriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type AIAssignmentState = "pending" | "running" | "completed" | "failed";
export type ActivityEventType =
  | "ISSUE_CREATED"
  | "STATUS_CHANGED"
  | "PRIORITY_CHANGED"
  | "ASSIGNEE_CHANGED"
  | "LABEL_ADDED"
  | "LABEL_REMOVED"
  | "COMMENT_ADDED"
  | "TITLE_CHANGED"
  | "DESCRIPTION_CHANGED";

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
