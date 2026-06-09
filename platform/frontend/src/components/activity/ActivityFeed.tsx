import { ActivityEvent, ActivityEventType } from "@/types";
import { formatRelativeTime } from "@/lib/utils";

function isUrl(value?: string): boolean {
  if (!value) return false;
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function eventDescription(event: ActivityEvent): string {
  const actor = event.user?.name || event.user?.email || "Someone";
  switch (event.eventType) {
    case "ISSUE_CREATED":
      return `${actor} created this issue`;
    case "STATUS_CHANGED":
      return `${actor} changed status from ${event.oldValue} to ${event.newValue}`;
    case "PRIORITY_CHANGED":
      return `${actor} changed priority from ${event.oldValue} to ${event.newValue}`;
    case "ASSIGNEE_CHANGED":
      return event.newValue
        ? `${actor} assigned this issue`
        : `${actor} unassigned this issue`;
    case "LABEL_ADDED":
      return `${actor} added a label`;
    case "LABEL_REMOVED":
      return `${actor} removed a label`;
    case "COMMENT_ADDED":
      return `${actor} commented`;
    case "TITLE_CHANGED":
      return `${actor} updated the title`;
    case "DESCRIPTION_CHANGED":
      return `${actor} updated the description`;
    case "ISSUE_CREATED_FROM_AI_DRAFT":
      return `${actor} created this issue from an AI draft`;
    case "AI_ASSIGNED":
      return `${actor} assigned this issue to Blocks AI`;
    case "AI_STATE_CHANGED":
      return `Blocks AI updated session state from ${event.oldValue} to ${event.newValue}`;
    case "AI_PR_OPENED":
      return `Blocks AI opened a pull request`;
    case "AI_COMPLETED":
      return `Blocks AI completed the implementation`;
    case "AI_FAILED":
      return `Blocks AI session failed`;
    case "AI_CANCELED":
      return `Blocks AI session was canceled`;
    default:
      return `${actor} made a change`;
  }
}

const eventIcons: Record<ActivityEventType, string> = {
  ISSUE_CREATED: "✦",
  STATUS_CHANGED: "◈",
  PRIORITY_CHANGED: "△",
  ASSIGNEE_CHANGED: "◎",
  LABEL_ADDED: "⬡",
  LABEL_REMOVED: "⬡",
  COMMENT_ADDED: "◇",
  TITLE_CHANGED: "✎",
  DESCRIPTION_CHANGED: "✎",
  ISSUE_CREATED_FROM_AI_DRAFT: "✦",
  AI_ASSIGNED: "◉",
  AI_STATE_CHANGED: "◉",
  AI_PR_OPENED: "◉",
  AI_COMPLETED: "◉",
  AI_FAILED: "◉",
  AI_CANCELED: "◉",
};

interface ActivityFeedProps {
  events: ActivityEvent[];
}

export function ActivityFeed({ events }: ActivityFeedProps) {
  if (events.length === 0) {
    return <p className="text-sm text-text-tertiary py-4">No activity yet</p>;
  }

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-text-primary">Activity</h3>
      <div className="relative">
        <div className="absolute left-3 top-0 bottom-0 w-px bg-border-default" />
        <ul className="space-y-4">
          {events.map((event) => (
            <li key={event.id} className="flex items-start gap-3 pl-8 relative">
              <div className="absolute left-1 top-0.5 h-5 w-5 rounded-full bg-bg-surface-hover flex items-center justify-center text-xs text-text-tertiary">
                {eventIcons[event.eventType] ?? "•"}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm text-text-secondary">{eventDescription(event)}</p>
                {event.eventType === "AI_PR_OPENED" && isUrl(event.newValue) && (
                  <a
                    href={event.newValue}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-1 inline-flex items-center gap-1.5 text-xs text-accent hover:underline"
                  >
                    <svg className="h-3 w-3 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                    </svg>
                    View Pull Request
                    <svg className="h-2.5 w-2.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                    </svg>
                  </a>
                )}
                <p className="text-xs text-text-tertiary mt-0.5">{formatRelativeTime(event.createdAt)}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
