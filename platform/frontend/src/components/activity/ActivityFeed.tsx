import { ActivityEvent, ActivityEventType } from "@/types";
import { formatRelativeTime } from "@/lib/utils";

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
};

interface ActivityFeedProps {
  events: ActivityEvent[];
}

export function ActivityFeed({ events }: ActivityFeedProps) {
  if (events.length === 0) {
    return <p className="text-sm text-gray-400 py-4">No activity yet</p>;
  }

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-700">Activity</h3>
      <div className="relative">
        <div className="absolute left-3 top-0 bottom-0 w-px bg-gray-200" />
        <ul className="space-y-4">
          {events.map((event) => (
            <li key={event.id} className="flex items-start gap-3 pl-8 relative">
              <div className="absolute left-1 top-0.5 h-5 w-5 rounded-full bg-gray-100 flex items-center justify-center text-xs text-gray-500">
                {eventIcons[event.eventType] ?? "•"}
              </div>
              <div>
                <p className="text-sm text-gray-700">{eventDescription(event)}</p>
                <p className="text-xs text-gray-400">{formatRelativeTime(event.createdAt)}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
