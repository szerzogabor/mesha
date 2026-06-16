"use client";

import { useState } from "react";
import {
  useAutomations,
  useCreateAutomation,
  useUpdateAutomation,
  useDeleteAutomation,
} from "@/hooks/useAutomations";
import { useProjectStatuses } from "@/hooks/useProjectStatuses";
import { useLabels, useCreateLabel } from "@/hooks/useLabels";
import {
  AutomationAction,
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
  STATUS_UPDATED: "Status updated to",
  LABEL_ADDED: "Labeled",
  AI_TOKEN_LIMIT_HIT: "AI ran out of tokens",
};

const PARAMETERIZED_TRIGGERS: AutomationTriggerType[] = ["STATUS_UPDATED", "LABEL_ADDED"];

const ACTION_LABELS: Record<AutomationActionType, string> = {
  SET_STATUS: "Move ticket to status",
  ADD_LABEL: "Add label to ticket",
  START_AI_SESSION: "Start AI session",
};

const PARAMETERLESS_ACTIONS: AutomationActionType[] = ["START_AI_SESSION"];

const PRESET_COLORS = [
  "#94a3b8", "#3b82f6", "#f59e0b", "#8b5cf6", "#22c55e",
  "#ef4444", "#f97316", "#06b6d4", "#ec4899", "#84cc16",
];

// Editor rows carry a client-side key so React state (e.g. the inline label
// mini-form) stays attached to the right row when rows are removed.
type EditableAction = AutomationAction & { key: string };

function newAction(): EditableAction {
  return { key: crypto.randomUUID(), actionType: "SET_STATUS", actionValue: "" };
}

function isActionComplete(action: EditableAction): boolean {
  if (PARAMETERLESS_ACTIONS.includes(action.actionType)) return true;
  return !!action.actionValue;
}

function toEditable(actions: AutomationAction[]): EditableAction[] {
  return actions.map((a) => ({ ...a, key: crypto.randomUUID() }));
}

function toRequest(actions: EditableAction[]): AutomationAction[] {
  return actions.map(({ actionType, actionValue }) => ({
    actionType,
    actionValue: PARAMETERLESS_ACTIONS.includes(actionType) ? undefined : actionValue,
  }));
}

const selectClass =
  "border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent";

function actionValueLabel(action: AutomationAction, labels: Label[]): string {
  if (action.actionType === "START_AI_SESSION") return "";
  if (action.actionType === "SET_STATUS") {
    return statusLabel(action.actionValue ?? "");
  }
  const label = labels.find((l) => l.id === action.actionValue);
  return label ? label.name : "(deleted label)";
}

function triggerValueLabel(
  triggerType: AutomationTriggerType,
  triggerValue: string | undefined,
  statuses: ProjectStatus[],
  labels: Label[]
): string {
  if (!triggerValue) return "";
  if (triggerType === "STATUS_UPDATED") {
    return statusLabel(triggerValue);
  }
  if (triggerType === "LABEL_ADDED") {
    const label = labels.find((l) => l.id === triggerValue);
    return label ? label.name : "(deleted label)";
  }
  return triggerValue;
}

interface TriggerValueSelectorProps {
  workspaceId: string;
  triggerType: AutomationTriggerType;
  value: string;
  onChange: (value: string) => void;
  statuses: ProjectStatus[];
  labels: Label[];
}

function TriggerValueSelector({
  triggerType,
  value,
  onChange,
  statuses,
  labels,
}: TriggerValueSelectorProps) {
  if (!PARAMETERIZED_TRIGGERS.includes(triggerType)) return null;

  const options =
    triggerType === "STATUS_UPDATED"
      ? statuses.map((s) => ({ value: s.name, label: statusLabel(s.name) }))
      : labels.map((l) => ({ value: l.id, label: l.name }));

  const placeholder = triggerType === "STATUS_UPDATED" ? "Select status..." : "Select label...";

  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={selectClass}
    >
      <option value="" disabled>{placeholder}</option>
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  );
}

interface ActionValueSelectorProps {
  workspaceId: string;
  actionType: AutomationActionType;
  value: string | undefined;
  onChange: (value: string) => void;
  statuses: ProjectStatus[];
  labels: Label[];
}

function ActionValueSelector({
  workspaceId,
  actionType,
  value,
  onChange,
  statuses,
  labels,
}: ActionValueSelectorProps) {
  const [creatingLabel, setCreatingLabel] = useState(false);
  const [labelName, setLabelName] = useState("");
  const [labelColor, setLabelColor] = useState(PRESET_COLORS[0]);
  const [labelError, setLabelError] = useState<string | null>(null);
  const [pendingLabel, setPendingLabel] = useState<{ id: string; name: string } | null>(null);
  const createLabel = useCreateLabel(workspaceId);

  if (PARAMETERLESS_ACTIONS.includes(actionType)) return null;

  const valueOptions =
    actionType === "SET_STATUS"
      ? statuses.map((s) => ({ value: s.name, label: statusLabel(s.name) }))
      : labels.map((l) => ({ value: l.id, label: l.name }));

  // Keep the selected value resolvable when it isn't in the fetched labels yet:
  // a just-created label (cache refetch still in flight) or a since-deleted one.
  if (actionType === "ADD_LABEL" && value && !labels.some((l) => l.id === value)) {
    valueOptions.push({
      value,
      label: pendingLabel?.id === value ? pendingLabel.name : "(deleted label)",
    });
  }

  const handleCreateLabel = async () => {
    if (!labelName.trim() || createLabel.isPending) return;
    setLabelError(null);
    try {
      const newLabel = await createLabel.mutateAsync({ name: labelName.trim(), color: labelColor });
      setPendingLabel({ id: newLabel.id, name: newLabel.name });
      onChange(newLabel.id);
      setLabelName("");
      setLabelColor(PRESET_COLORS[0]);
      setCreatingLabel(false);
    } catch (err) {
      setLabelError(err instanceof Error ? err.message : "Failed to create label");
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-2 items-center">
        <select
          value={value ?? ""}
          onChange={(e) => onChange(e.target.value)}
          className={selectClass}
        >
          <option value="" disabled>
            {actionType === "SET_STATUS" ? "Select status..." : "Select label..."}
          </option>
          {valueOptions.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
        {actionType === "ADD_LABEL" && !creatingLabel && (
          <button
            type="button"
            onClick={() => setCreatingLabel(true)}
            className="px-2 py-1.5 text-sm text-accent hover:text-accent-hover whitespace-nowrap transition-colors"
          >
            + New label
          </button>
        )}
      </div>

      {actionType === "ADD_LABEL" && creatingLabel && (
        <div className="p-3 border border-border-default rounded-lg bg-bg-surface-hover">
          <div className="flex gap-2 items-center flex-wrap">
            <input
              autoFocus
              type="text"
              value={labelName}
              onChange={(e) => setLabelName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  handleCreateLabel();
                }
              }}
              maxLength={50}
              placeholder="Label name"
              className="flex-1 min-w-36 border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent"
            />
            <div className="flex flex-wrap gap-1.5">
              {PRESET_COLORS.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setLabelColor(c)}
                  aria-label={`Select color ${c}`}
                  className="w-5 h-5 rounded-full border-2 transition-transform hover:scale-110"
                  style={{
                    backgroundColor: c,
                    borderColor: labelColor === c ? "white" : "transparent",
                    boxShadow: labelColor === c ? `0 0 0 2px ${c}` : undefined,
                  }}
                />
              ))}
            </div>
            <button
              type="button"
              onClick={handleCreateLabel}
              disabled={!labelName.trim() || createLabel.isPending}
              className="px-3 py-1.5 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover disabled:opacity-50 transition-colors"
            >
              {createLabel.isPending ? "Adding..." : "Add"}
            </button>
            <button
              type="button"
              onClick={() => {
                setCreatingLabel(false);
                setLabelName("");
                setLabelError(null);
              }}
              className="px-2 py-1.5 text-sm text-text-secondary hover:text-text-primary rounded-lg hover:bg-bg-surface-hover transition-colors"
            >
              Cancel
            </button>
          </div>
          {labelError && <p className="text-xs text-destructive mt-2">{labelError}</p>}
        </div>
      )}
    </div>
  );
}

interface ActionsEditorProps {
  workspaceId: string;
  actions: EditableAction[];
  onChange: (actions: EditableAction[]) => void;
  statuses: ProjectStatus[];
  labels: Label[];
}

function ActionsEditor({ workspaceId, actions, onChange, statuses, labels }: ActionsEditorProps) {
  const updateAction = (index: number, patch: Partial<AutomationAction>) => {
    onChange(actions.map((a, i) => (i === index ? { ...a, ...patch } : a)));
  };

  return (
    <div className="flex flex-col gap-2">
      {actions.map((action, index) => (
        <div key={action.key} className="flex gap-2 items-start flex-wrap">
          <span className="text-sm text-text-secondary py-1.5 w-9">
            {index === 0 ? "then" : "and"}
          </span>
          <select
            value={action.actionType}
            onChange={(e) =>
              updateAction(index, {
                actionType: e.target.value as AutomationActionType,
                actionValue: "",
              })
            }
            className={selectClass}
          >
            {(Object.keys(ACTION_LABELS) as AutomationActionType[]).map((a) => (
              <option key={a} value={a}>{ACTION_LABELS[a]}</option>
            ))}
          </select>

          <ActionValueSelector
            workspaceId={workspaceId}
            actionType={action.actionType}
            value={action.actionValue}
            onChange={(value) => updateAction(index, { actionValue: value })}
            statuses={statuses}
            labels={labels}
          />

          {actions.length > 1 && (
            <button
              type="button"
              onClick={() => onChange(actions.filter((_, i) => i !== index))}
              aria-label="Remove action"
              className="p-1.5 mt-0.5 text-text-tertiary hover:text-destructive rounded hover:bg-bg-surface-hover transition-colors"
              title="Remove action"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}
        </div>
      ))}
      <button
        type="button"
        onClick={() => onChange([...actions, newAction()])}
        className="self-start ml-11 text-sm text-accent hover:text-accent-hover transition-colors"
      >
        + Add action
      </button>
    </div>
  );
}

function ruleSentence(rule: AutomationRule, statuses: ProjectStatus[], labels: Label[]) {
  const tvLabel = rule.triggerValue
    ? triggerValueLabel(rule.triggerType, rule.triggerValue, statuses, labels)
    : null;
  return (
    <>
      When{" "}
      <span className="font-medium">
        {TRIGGER_LABELS[rule.triggerType]}
        {tvLabel ? ` "${tvLabel}"` : ""}
      </span>
      {rule.actions.map((action, index) => {
        const valLabel = actionValueLabel(action, labels);
        return (
          <span key={index}>
            {index === 0 ? " → " : " and "}
            <span className="font-medium">
              {ACTION_LABELS[action.actionType]}{valLabel ? `: ${valLabel}` : ""}
            </span>
          </span>
        );
      })}
    </>
  );
}

interface RuleRowProps {
  rule: AutomationRule;
  workspaceId: string;
  projectId: string;
  statuses: ProjectStatus[];
  labels: Label[];
}

function RuleRow({ rule, workspaceId, projectId, statuses, labels }: RuleRowProps) {
  const [editing, setEditing] = useState(false);
  const [triggerType, setTriggerType] = useState<AutomationTriggerType>(rule.triggerType);
  const [triggerValue, setTriggerValue] = useState<string>(rule.triggerValue ?? "");
  const [actions, setActions] = useState<EditableAction[]>(() => toEditable(rule.actions));
  const [error, setError] = useState<string | null>(null);
  const updateRule = useUpdateAutomation(projectId);
  const deleteRule = useDeleteAutomation(projectId);

  const triggerValueComplete = !PARAMETERIZED_TRIGGERS.includes(triggerType) || triggerValue !== "";
  const allActionsComplete = actions.length > 0 && actions.every((a) => isActionComplete(a)) && triggerValueComplete;

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

  const handleSave = async () => {
    if (!allActionsComplete || updateRule.isPending) return;
    setError(null);
    try {
      await updateRule.mutateAsync({
        ruleId: rule.id,
        data: {
          triggerType,
          triggerValue: PARAMETERIZED_TRIGGERS.includes(triggerType) ? triggerValue : undefined,
          actions: toRequest(actions),
        },
      });
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update rule");
    }
  };

  const handleCancel = () => {
    setTriggerType(rule.triggerType);
    setTriggerValue(rule.triggerValue ?? "");
    setActions(toEditable(rule.actions));
    setError(null);
    setEditing(false);
  };

  if (editing) {
    return (
      <div className="p-3 bg-bg-surface border border-border-default rounded-lg">
        <div className="flex gap-2 items-center mb-2 flex-wrap">
          <span className="text-sm text-text-secondary w-9">When</span>
          <select
            value={triggerType}
            onChange={(e) => {
              setTriggerType(e.target.value as AutomationTriggerType);
              setTriggerValue("");
            }}
            className={selectClass}
          >
            {(Object.keys(TRIGGER_LABELS) as AutomationTriggerType[]).map((t) => (
              <option key={t} value={t}>{TRIGGER_LABELS[t]}</option>
            ))}
          </select>
          <TriggerValueSelector
            workspaceId={workspaceId}
            triggerType={triggerType}
            value={triggerValue}
            onChange={setTriggerValue}
            statuses={statuses}
            labels={labels}
          />
        </div>

        <ActionsEditor
          workspaceId={workspaceId}
          actions={actions}
          onChange={setActions}
          statuses={statuses}
          labels={labels}
        />

        <div className="flex gap-2 mt-3">
          <button
            onClick={handleSave}
            disabled={!allActionsComplete || updateRule.isPending}
            className="px-3 py-1.5 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover disabled:opacity-50 transition-colors"
          >
            {updateRule.isPending ? "Saving..." : "Save"}
          </button>
          <button
            onClick={handleCancel}
            className="px-3 py-1.5 text-sm text-text-secondary hover:text-text-primary rounded-lg hover:bg-bg-surface-hover transition-colors"
          >
            Cancel
          </button>
        </div>
        {error && <p className="text-xs text-destructive mt-2">{error}</p>}
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3 p-3 bg-bg-surface border border-border-default rounded-lg group">
      <span
        className={`w-2 h-2 rounded-full flex-shrink-0 ${rule.enabled ? "bg-accent" : "bg-text-tertiary"}`}
        title={rule.enabled ? "Enabled" : "Disabled"}
      />
      <span className={`flex-1 text-sm ${rule.enabled ? "text-text-primary" : "text-text-tertiary"}`}>
        {ruleSentence(rule, statuses, labels)}
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
          onClick={() => {
            setTriggerType(rule.triggerType);
            setTriggerValue(rule.triggerValue ?? "");
            setActions(toEditable(rule.actions));
            setError(null);
            setEditing(true);
          }}
          className="p-1.5 text-text-tertiary hover:text-text-primary rounded hover:bg-bg-surface-hover transition-colors"
          title="Edit"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
          </svg>
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
  workspaceId: string;
  projectId: string;
  statuses: ProjectStatus[];
  labels: Label[];
}

function CreateRuleForm({ workspaceId, projectId, statuses, labels }: CreateRuleFormProps) {
  const [triggerType, setTriggerType] = useState<AutomationTriggerType>("PR_OPENED");
  const [triggerValue, setTriggerValue] = useState<string>("");
  const [actions, setActions] = useState<EditableAction[]>(() => [newAction()]);
  const [error, setError] = useState<string | null>(null);
  const createRule = useCreateAutomation(projectId);

  const triggerValueComplete = !PARAMETERIZED_TRIGGERS.includes(triggerType) || triggerValue !== "";
  const allActionsComplete = actions.length > 0 && actions.every((a) => isActionComplete(a)) && triggerValueComplete;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!allActionsComplete || createRule.isPending) return;
    setError(null);
    try {
      await createRule.mutateAsync({
        triggerType,
        triggerValue: PARAMETERIZED_TRIGGERS.includes(triggerType) ? triggerValue : undefined,
        actions: toRequest(actions),
      });
      setTriggerValue("");
      setActions([newAction()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create rule");
    }
  };

  return (
    <form onSubmit={handleSubmit} className="p-4 bg-bg-surface border border-border-default rounded-lg">
      <h3 className="text-sm font-semibold text-text-primary mb-3">Create New Rule</h3>

      <div className="flex gap-2 items-center mb-2 flex-wrap">
        <span className="text-sm text-text-secondary w-9">When</span>
        <select
          value={triggerType}
          onChange={(e) => {
            setTriggerType(e.target.value as AutomationTriggerType);
            setTriggerValue("");
          }}
          className={selectClass}
        >
          {(Object.keys(TRIGGER_LABELS) as AutomationTriggerType[]).map((t) => (
            <option key={t} value={t}>{TRIGGER_LABELS[t]}</option>
          ))}
        </select>
        <TriggerValueSelector
          workspaceId={workspaceId}
          triggerType={triggerType}
          value={triggerValue}
          onChange={setTriggerValue}
          statuses={statuses}
          labels={labels}
        />
      </div>

      <ActionsEditor
        workspaceId={workspaceId}
        actions={actions}
        onChange={setActions}
        statuses={statuses}
        labels={labels}
      />

      <div className="mt-3">
        <button
          type="submit"
          disabled={!allActionsComplete || createRule.isPending}
          className="px-4 py-1.5 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover disabled:opacity-50 transition-colors"
        >
          {createRule.isPending ? "Creating..." : "Create"}
        </button>
      </div>
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
        and add a label when its pull request is opened.
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
              workspaceId={workspaceId}
              projectId={projectId}
              statuses={statuses}
              labels={labels}
            />
          ))}
        </div>
      )}

      <CreateRuleForm
        workspaceId={workspaceId}
        projectId={projectId}
        statuses={statuses}
        labels={labels}
      />
    </div>
  );
}
