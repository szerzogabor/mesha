"use client";

import { useState } from "react";
import {
  useAutomations,
  useCreateAutomation,
  useUpdateAutomation,
  useDeleteAutomation,
} from "@/hooks/useAutomations";
import { useProjectStatuses } from "@/hooks/useProjectStatuses";
import { useLabels } from "@/hooks/useLabels";
import {
  AutomationActionType,
  AutomationRule,
  AutomationTriggerType,
  Label,
  ProjectStatus,
} from "@/types";
import { statusLabel } from "@/lib/utils";

const TRIGGER_LABELS: Record<AutomationTriggerType, string> = {
  PR_OPENED: "Pull request opened",
  PR_MERGED: "Pull request merged",
  PR_CLOSED: "Pull request closed",
  BLOCKS_SESSION_STARTED: "Blocks session started",
  BLOCKS_SESSION_COMPLETED: "Blocks session completed",
  BLOCKS_SESSION_FAILED: "Blocks session failed",
};

const ACTION_LABELS: Record<AutomationActionType, string> = {
  SET_STATUS: "Move ticket to status",
  ADD_LABEL: "Add label to ticket",
};

const selectClass =
  "border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent";

function actionValueLabel(rule: AutomationRule, labels: Label[]): string {
  if (rule.actionType === "SET_STATUS") {
    return statusLabel(rule.actionValue);
  }
  const label = labels.find((l) => l.id === rule.actionValue);
  return label ? label.name : "(deleted label)";
}

interface RuleRowProps {
  rule: AutomationRule;
  projectId: string;
  labels: Label[];
}

function RuleRow({ rule, projectId, labels }: RuleRowProps) {
  const updateRule = useUpdateAutomation(projectId);
  const deleteRule = useDeleteAutomation(projectId);

  const handleToggle = () => {
    updateRule.mutate({ ruleId: rule.id, data: { enabled: !rule.enabled } });
  };

  const handleDelete = async () => {
    if (!confirm("Delete this automation rule?")) return;
    try {
      await deleteRule.mutateAsync(rule.id);
    } catch (err) {
      console.error("Failed to delete automation rule:", err);
    }
  };

  return (
    <div className="flex items-center gap-3 p-3 bg-bg-surface border border-border-default rounded-lg group">
      <span
        className={`w-2 h-2 rounded-full flex-shrink-0 ${rule.enabled ? "bg-accent" : "bg-text-tertiary"}`}
        title={rule.enabled ? "Enabled" : "Disabled"}
      />
      <span className={`flex-1 text-sm ${rule.enabled ? "text-text-primary" : "text-text-tertiary"}`}>
        When <span className="font-medium">{TRIGGER_LABELS[rule.triggerType]}</span>
        {" → "}
        <span className="font-medium">
          {ACTION_LABELS[rule.actionType]}: {actionValueLabel(rule, labels)}
        </span>
      </span>

      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={handleToggle}
          disabled={updateRule.isPending}
          className="px-2 py-1 text-xs text-text-secondary hover:text-text-primary rounded hover:bg-bg-surface-hover transition-colors"
          title={rule.enabled ? "Disable" : "Enable"}
        >
          {rule.enabled ? "Disable" : "Enable"}
        </button>
        <button
          onClick={handleDelete}
          disabled={deleteRule.isPending}
          className="p-1.5 text-text-tertiary hover:text-destructive rounded hover:bg-bg-surface-hover transition-colors"
          title="Delete"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="3 6 5 6 21 6" />
            <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
            <path d="M10 11v6M14 11v6" />
            <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
          </svg>
        </button>
      </div>
    </div>
  );
}

interface CreateRuleFormProps {
  projectId: string;
  statuses: ProjectStatus[];
  labels: Label[];
}

function CreateRuleForm({ projectId, statuses, labels }: CreateRuleFormProps) {
  const [triggerType, setTriggerType] = useState<AutomationTriggerType>("PR_OPENED");
  const [actionType, setActionType] = useState<AutomationActionType>("SET_STATUS");
  const [actionValue, setActionValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const createRule = useCreateAutomation(projectId);

  const valueOptions =
    actionType === "SET_STATUS"
      ? statuses.map((s) => ({ value: s.name, label: statusLabel(s.name) }))
      : labels.map((l) => ({ value: l.id, label: l.name }));

  const handleActionTypeChange = (next: AutomationActionType) => {
    setActionType(next);
    setActionValue("");
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!actionValue) return;
    setError(null);
    try {
      await createRule.mutateAsync({ triggerType, actionType, actionValue });
      setActionValue("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create rule");
    }
  };

  return (
    <form onSubmit={handleSubmit} className="p-4 bg-bg-surface border border-border-default rounded-lg">
      <h3 className="text-sm font-semibold text-text-primary mb-3">Create New Rule</h3>
      <div className="flex gap-3 items-center flex-wrap">
        <span className="text-sm text-text-secondary">When</span>
        <select
          value={triggerType}
          onChange={(e) => setTriggerType(e.target.value as AutomationTriggerType)}
          className={selectClass}
        >
          {(Object.keys(TRIGGER_LABELS) as AutomationTriggerType[]).map((t) => (
            <option key={t} value={t}>{TRIGGER_LABELS[t]}</option>
          ))}
        </select>

        <span className="text-sm text-text-secondary">then</span>
        <select
          value={actionType}
          onChange={(e) => handleActionTypeChange(e.target.value as AutomationActionType)}
          className={selectClass}
        >
          {(Object.keys(ACTION_LABELS) as AutomationActionType[]).map((a) => (
            <option key={a} value={a}>{ACTION_LABELS[a]}</option>
          ))}
        </select>

        <select
          value={actionValue}
          onChange={(e) => setActionValue(e.target.value)}
          className={selectClass}
        >
          <option value="" disabled>
            {actionType === "SET_STATUS" ? "Select status..." : "Select label..."}
          </option>
          {valueOptions.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>

        <button
          type="submit"
          disabled={!actionValue || createRule.isPending}
          className="px-4 py-1.5 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover disabled:opacity-50 transition-colors"
        >
          {createRule.isPending ? "Creating..." : "Create"}
        </button>
      </div>
      {actionType === "ADD_LABEL" && labels.length === 0 && (
        <p className="text-xs text-text-tertiary mt-2">
          No labels exist in this workspace yet. Create one from a ticket first.
        </p>
      )}
      {error && <p className="text-xs text-destructive mt-2">{error}</p>}
    </form>
  );
}

interface AutomationRulesSectionProps {
  workspaceId: string;
  projectId: string;
}

export default function AutomationRulesSection({ workspaceId, projectId }: AutomationRulesSectionProps) {
  const rulesQuery = useAutomations(projectId);
  const statusesQuery = useProjectStatuses(projectId);
  const labelsQuery = useLabels(workspaceId);

  const rules = rulesQuery.data ?? [];
  const statuses = statusesQuery.data ?? [];
  const labels = labelsQuery.data ?? [];

  return (
    <div className="mb-6">
      <h3 className="text-base font-semibold text-text-primary mb-1">Automations</h3>
      <p className="text-sm text-text-tertiary mb-4">
        Automatically update tickets when something happens — for example, move a ticket to In Review
        when its pull request is opened.
      </p>

      {rulesQuery.isLoading ? (
        <div className="text-sm text-text-tertiary">Loading rules...</div>
      ) : (
        <div className="flex flex-col gap-2 mb-4">
          {rules.length === 0 && (
            <div className="text-sm text-text-tertiary p-3 bg-bg-surface border border-border-default rounded-lg">
              No automation rules yet.
            </div>
          )}
          {rules.map((rule) => (
            <RuleRow
              key={rule.id}
              rule={rule}
              projectId={projectId}
              labels={labels}
            />
          ))}
        </div>
      )}

      <CreateRuleForm projectId={projectId} statuses={statuses} labels={labels} />
    </div>
  );
}
