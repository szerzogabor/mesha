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

export type IssueStatus = string;

export interface ProjectStatus {
  id: string;
  projectId: string;
  name: string;
  color: string;
  position: number;
  createdAt: string;
}
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

export type AgentType = "BLOCKS";
export type AgentLlm = "claude" | "codex";

export type AgentProviderType = "BLOCKS";

export interface AgentDefinition {
  id: string;
  workspaceId: string;
  name: string;
  title: string;
  description?: string;
  providerType: AgentProviderType;
  systemPrompt: string;
  providerParameters: Record<string, unknown>;
  blocksAgentName?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface IssueAgentAssignment {
  id: string;
  issueId: string;
  agentDefinitionId: string;
  agentTitle: string;
  agentName: string;
  providerType: AgentProviderType;
  agentActive: boolean;
  assignedAt: string;
  assignedBy?: string;
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
  agentType?: AgentType;
  agentLlm?: AgentLlm;
  createdAt: string;
  updatedAt: string;
  lastPullRequest?: LinkedPullRequest;
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
  linkedPullRequests?: LinkedPullRequest[];
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

export type IssueLinkType =
  | "DEPENDS_ON"
  | "BLOCKS"
  | "DUPLICATE_OF"
  | "PARENT_OF"
  | "CHILD_OF";

export interface LinkedIssueSummary {
  id: string;
  projectId?: string;
  identifier?: string;
  title: string;
  status: string;
}

export interface IssueLink {
  id: string;
  linkType: IssueLinkType;
  sourceIssue: LinkedIssueSummary;
  targetIssue: LinkedIssueSummary;
  createdAt: string;
}

export type TicketRuleConditionType = "HAS_STATUS" | "HAS_LABEL" | "ASSIGNED_TO_AGENT" | "ASSIGNED_TO_HUMAN";
export type TicketRuleRestrictionType = "CANNOT_START_AI_SESSION" | "CANNOT_MOVE_TO_STATUS";

export interface TicketRuleCondition {
  id: string;
  conditionType: TicketRuleConditionType;
  conditionValue?: string;
}

export interface TicketRuleRestriction {
  id: string;
  restrictionType: TicketRuleRestrictionType;
  restrictionValue?: string;
}

export interface TicketRule {
  id: string;
  projectId: string;
  name: string;
  conditions: TicketRuleCondition[];
  restrictions: TicketRuleRestriction[];
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export type AutomationTriggerType =
  | "PR_OPENED"
  | "PR_MERGED"
  | "PR_CLOSED"
  | "BLOCKS_SESSION_STARTED"
  | "BLOCKS_SESSION_COMPLETED"
  | "BLOCKS_SESSION_FAILED"
  | "STATUS_UPDATED"
  | "LABEL_ADDED"
  | "AI_TOKEN_LIMIT_HIT";

export type AutomationActionType = "SET_STATUS" | "ADD_LABEL" | "START_AI_SESSION";

export type AutomationActionConditionType = "HAS_STATUS" | "HAS_LABEL";

export interface AutomationActionCondition {
  conditionType: AutomationActionConditionType;
  conditionValue: string;
  position?: number;
}

export interface AutomationAction {
  actionType: AutomationActionType;
  actionValue?: string;
  conditions?: AutomationActionCondition[];
}

export interface AutomationRule {
  id: string;
  projectId: string;
  triggerType: AutomationTriggerType;
  triggerValue?: string;
  actions: AutomationAction[];
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}
