"use client";

import { useState } from "react";
import {
  useTicketRules,
  useCreateTicketRule,
  useUpdateTicketRule,
  useDeleteTicketRule,
  TicketRuleConditionRequest,
  TicketRuleRestrictionRequest,
} from "@/hooks/useTicketRules";
import { useProjectStatuses } from "@/hooks/useProjectStatuses";
import { useLabels } from "@/hooks/useLabels";
import {
  Label,
  ProjectStatus,
  TicketRule,
  TicketRuleConditionType,
  TicketRuleRestrictionType,
} from "@/types";
import { statusLabel } from "@/lib/utils";

const CONDITION_LABELS: Record<TicketRuleConditionType, string> = {
  HAS_STATUS: "Ticket is in status",
  HAS_LABEL: "Ticket has label",
  ASSIGNED_TO_AGENT: "Ticket is assigned to agent",
  ASSIGNED_TO_HUMAN: "Ticket is assigned to human",
};

const NO_VALUE_CONDITIONS: TicketRuleConditionType[] = ["ASSIGNED_TO_AGENT", "ASSIGNED_TO_HUMAN"];

const RESTRICTION_LABELS: Record<TicketRuleRestrictionType, string> = {
  CANNOT_START_AI_SESSION: "Cannot start AI session",
  CANNOT_MOVE_TO_STATUS: "Cannot move to status",
};

const selectClass =
  "border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent";

type EditableCondition = TicketRuleConditionRequest & { key: string };
type EditableRestriction = TicketRuleRestrictionRequest & { key: string };

function newCondition(): EditableCondition {
  return { key: crypto.randomUUID(), conditionType: "HAS_STATUS", conditionValue: "" };
}

function newRestriction(): EditableRestriction {
  return { key: crypto.randomUUID(), restrictionType: "CANNOT_START_AI_SESSION", restrictionValue: undefined };
}

function conditionValueLabel(
  conditionType: TicketRuleConditionType,
  conditionValue: string | undefined,
  statuses: ProjectStatus[],
  labels: Label[]
): string {
  if (NO_VALUE_CONDITIONS.includes(conditionType)) return "";
  if (!conditionValue) return "";
  if (conditionType === "HAS_STATUS") return statusLabel(conditionValue);
  const label = labels.find((l) => l.id === conditionValue);
  return label ? label.name : "(deleted label)";
}

function restrictionValueLabel(
  restrictionType: TicketRuleRestrictionType,
  restrictionValue: string | undefined
): string {
  if (restrictionType === "CANNOT_MOVE_TO_STATUS" && restrictionValue) {
    return statusLabel(restrictionValue);
  }
  return "";
}

function ruleSummary(rule: TicketRule, statuses: ProjectStatus[], labels: Label[]) {
  const conditionParts = rule.conditions.map((c) => {
    const valLabel = conditionValueLabel(c.conditionType, c.conditionValue, statuses, labels);
    return valLabel
      ? `${CONDITION_LABELS[c.conditionType]}: "${valLabel}"`
      : CONDITION_LABELS[c.conditionType];
  });

  const restrictionParts = rule.restrictions.map((r) => {
    const valLabel = restrictionValueLabel(r.restrictionType, r.restrictionValue);
    return valLabel
      ? `${RESTRICTION_LABELS[r.restrictionType]}: "${valLabel}"`
      : RESTRICTION_LABELS[r.restrictionType];
  });

  return (
    <>
      {conditionParts.length > 0 && (
        <span>
          If{" "}
          {conditionParts.map((p, i) => (
            <span key={i}>
              {i > 0 && " and "}
              <span className="font-medium">{p}</span>
            </span>
          ))}
          {" → "}
        </span>
      )}
      {restrictionParts.map((p, i) => (
        <span key={i}>
          {i > 0 && ", "}
          <span className="font-medium">{p}</span>
        </span>
      ))}
    </>
  );
}

interface ConditionValueSelectorProps {
  conditionType: TicketRuleConditionType;
  value: string | undefined;
  onChange: (value: string) => void;
  statuses: ProjectStatus[];
  labels: Label[];
}

function ConditionValueSelector({ conditionType, value, onChange, statuses, labels }: ConditionValueSelectorProps) {
  if (NO_VALUE_CONDITIONS.includes(conditionType)) return null;

  const options =
    conditionType === "HAS_STATUS"
      ? statuses.map((s) => ({ value: s.name, label: statusLabel(s.name) }))
      : labels.map((l) => ({ value: l.id, label: l.name }));

  const placeholder = conditionType === "HAS_STATUS" ? "Select status..." : "Select label...";

  return (
    <select value={value ?? ""} onChange={(e) => onChange(e.target.value)} className={selectClass}>
      <option value="" disabled>{placeholder}</option>
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  );
}

interface RestrictionValueSelectorProps {
  restrictionType: TicketRuleRestrictionType;
  value: string | undefined;
  onChange: (value: string | undefined) => void;
  statuses: ProjectStatus[];
}

function RestrictionValueSelector({ restrictionType, value, onChange, statuses }: RestrictionValueSelectorProps) {
  if (restrictionType !== "CANNOT_MOVE_TO_STATUS") return null;

  return (
    <select value={value ?? ""} onChange={(e) => onChange(e.target.value || undefined)} className={selectClass}>
      <option value="" disabled>Select status...</option>
      {statuses.map((s) => (
        <option key={s.name} value={s.name}>{statusLabel(s.name)}</option>
      ))}
    </select>
  );
}

interface ConditionsEditorProps {
  conditions: EditableCondition[];
  onChange: (conditions: EditableCondition[]) => void;
  statuses: ProjectStatus[];
  labels: Label[];
}

function ConditionsEditor({ conditions, onChange, statuses, labels }: ConditionsEditorProps) {
  const update = (index: number, patch: Partial<EditableCondition>) => {
    onChange(conditions.map((c, i) => (i === index ? { ...c, ...patch } : c)));
  };

  return (
    <div className="flex flex-col gap-2">
      {conditions.map((condition, index) => (
        <div key={condition.key} className="flex gap-2 items-center flex-wrap">
          <span className="text-sm text-text-secondary py-1.5 w-6">
            {index === 0 ? "if" : "and"}
          </span>
          <select
            value={condition.conditionType}
            onChange={(e) => {
              const newType = e.target.value as TicketRuleConditionType;
              update(index, {
                conditionType: newType,
                conditionValue: NO_VALUE_CONDITIONS.includes(newType) ? undefined : "",
              });
            }}
            className={selectClass}
          >
            {(Object.keys(CONDITION_LABELS) as TicketRuleConditionType[]).map((t) => (
              <option key={t} value={t}>{CONDITION_LABELS[t]}</option>
            ))}
          </select>
          <ConditionValueSelector
            conditionType={condition.conditionType}
            value={condition.conditionValue}
            onChange={(v) => update(index, { conditionValue: v })}
            statuses={statuses}
            labels={labels}
          />
          {conditions.length > 1 && (
            <button
              type="button"
              onClick={() => onChange(conditions.filter((_, i) => i !== index))}
              aria-label="Remove condition"
              className="p-1.5 text-text-tertiary hover:text-destructive rounded hover:bg-bg-surface-hover transition-colors"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}
        </div>
      ))}
      <button
        type="button"
        onClick={() => onChange([...conditions, newCondition()])}
        className="self-start ml-9 text-sm text-accent hover:text-accent-hover transition-colors"
      >
        + Add condition
      </button>
    </div>
  );
}

interface RestrictionsEditorProps {
  restrictions: EditableRestriction[];
  onChange: (restrictions: EditableRestriction[]) => void;
  statuses: ProjectStatus[];
}

function RestrictionsEditor({ restrictions, onChange, statuses }: RestrictionsEditorProps) {
  const update = (index: number, patch: Partial<EditableRestriction>) => {
    onChange(restrictions.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  };

  return (
    <div className="flex flex-col gap-2">
      {restrictions.map((restriction, index) => (
        <div key={restriction.key} className="flex gap-2 items-center flex-wrap">
          <span className="text-sm text-text-secondary py-1.5 w-9">
            {index === 0 ? "then" : "and"}
          </span>
          <select
            value={restriction.restrictionType}
            onChange={(e) =>
              update(index, {
                restrictionType: e.target.value as TicketRuleRestrictionType,
                restrictionValue: undefined,
              })
            }
            className={selectClass}
          >
            {(Object.keys(RESTRICTION_LABELS) as TicketRuleRestrictionType[]).map((t) => (
              <option key={t} value={t}>{RESTRICTION_LABELS[t]}</option>
            ))}
          </select>
          <RestrictionValueSelector
            restrictionType={restriction.restrictionType}
            value={restriction.restrictionValue}
            onChange={(v) => update(index, { restrictionValue: v })}
            statuses={statuses}
          />
          {restrictions.length > 1 && (
            <button
              type="button"
              onClick={() => onChange(restrictions.filter((_, i) => i !== index))}
              aria-label="Remove restriction"
              className="p-1.5 text-text-tertiary hover:text-destructive rounded hover:bg-bg-surface-hover transition-colors"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}
        </div>
      ))}
      <button
        type="button"
        onClick={() => onChange([...restrictions, newRestriction()])}
        className="self-start ml-12 text-sm text-accent hover:text-accent-hover transition-colors"
      >
        + Add restriction
      </button>
    </div>
  );
}

function isComplete(conditions: EditableCondition[], restrictions: EditableRestriction[]): boolean {
  return (
    conditions.length > 0 &&
    conditions.every(
      (c) => NO_VALUE_CONDITIONS.includes(c.conditionType) || c.conditionValue !== ""
    ) &&
    restrictions.length > 0 &&
    restrictions.every(
      (r) =>
        r.restrictionType === "CANNOT_START_AI_SESSION" ||
        (r.restrictionType === "CANNOT_MOVE_TO_STATUS" && !!r.restrictionValue)
    )
  );
}

interface RuleRowProps {
  rule: TicketRule;
  projectId: string;
  statuses: ProjectStatus[];
  labels: Label[];
}

function RuleRow({ rule, projectId, statuses, labels }: RuleRowProps) {
  const [editing, setEditing] = useState(false);
  const [conditions, setConditions] = useState<EditableCondition[]>(() =>
    rule.conditions.map((c) => ({ ...c, key: crypto.randomUUID() }))
  );
  const [restrictions, setRestrictions] = useState<EditableRestriction[]>(() =>
    rule.restrictions.map((r) => ({ ...r, key: crypto.randomUUID() }))
  );
  const [error, setError] = useState<string | null>(null);
  const updateRule = useUpdateTicketRule(projectId);
  const deleteRule = useDeleteTicketRule(projectId);

  const handleToggle = () => {
    updateRule.mutate({ ruleId: rule.id, data: { enabled: !rule.enabled } });
  };

  const handleDelete = async () => {
    if (!confirm("Delete this ticket rule?")) return;
    try {
      await deleteRule.mutateAsync(rule.id);
    } catch (err) {
      console.error("Failed to delete ticket rule:", err);
    }
  };

  const handleSave = async () => {
    if (!isComplete(conditions, restrictions) || updateRule.isPending) return;
    setError(null);
    try {
      await updateRule.mutateAsync({
        ruleId: rule.id,
        data: {
          conditions: conditions.map(({ conditionType, conditionValue }) => ({
            conditionType,
            conditionValue,
          })),
          restrictions: restrictions.map(({ restrictionType, restrictionValue }) => ({
            restrictionType,
            restrictionValue,
          })),
        },
      });
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update rule");
    }
  };

  const handleCancel = () => {
    setConditions(rule.conditions.map((c) => ({ ...c, key: crypto.randomUUID() })));
    setRestrictions(rule.restrictions.map((r) => ({ ...r, key: crypto.randomUUID() })));
    setError(null);
    setEditing(false);
  };

  if (editing) {
    return (
      <div className="p-3 bg-bg-surface border border-border-default rounded-lg">
        <div className="mb-3">
          <ConditionsEditor
            conditions={conditions}
            onChange={setConditions}
            statuses={statuses}
            labels={labels}
          />
        </div>
        <RestrictionsEditor
          restrictions={restrictions}
          onChange={setRestrictions}
          statuses={statuses}
        />
        <div className="flex gap-2 mt-3">
          <button
            onClick={handleSave}
            disabled={!isComplete(conditions, restrictions) || updateRule.isPending}
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
        {ruleSummary(rule, statuses, labels)}
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
            setConditions(rule.conditions.map((c) => ({ ...c, key: crypto.randomUUID() })));
            setRestrictions(rule.restrictions.map((r) => ({ ...r, key: crypto.randomUUID() })));
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
  projectId: string;
  statuses: ProjectStatus[];
  labels: Label[];
}

function CreateRuleForm({ projectId, statuses, labels }: CreateRuleFormProps) {
  const [name, setName] = useState("");
  const [conditions, setConditions] = useState<EditableCondition[]>(() => [newCondition()]);
  const [restrictions, setRestrictions] = useState<EditableRestriction[]>(() => [newRestriction()]);
  const [error, setError] = useState<string | null>(null);
  const createRule = useCreateTicketRule(projectId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !isComplete(conditions, restrictions) || createRule.isPending) return;
    setError(null);
    try {
      await createRule.mutateAsync({
        name: name.trim(),
        conditions: conditions.map(({ conditionType, conditionValue }) => ({
          conditionType,
          conditionValue,
        })),
        restrictions: restrictions.map(({ restrictionType, restrictionValue }) => ({
          restrictionType,
          restrictionValue,
        })),
      });
      setName("");
      setConditions([newCondition()]);
      setRestrictions([newRestriction()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create rule");
    }
  };

  return (
    <form onSubmit={handleSubmit} className="p-4 bg-bg-surface border border-border-default rounded-lg">
      <h3 className="text-sm font-semibold text-text-primary mb-3">Create New Rule</h3>
      <div className="mb-3">
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Rule name"
          className="w-full border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent"
        />
      </div>
      <div className="mb-3">
        <ConditionsEditor
          conditions={conditions}
          onChange={setConditions}
          statuses={statuses}
          labels={labels}
        />
      </div>
      <RestrictionsEditor
        restrictions={restrictions}
        onChange={setRestrictions}
        statuses={statuses}
      />
      <div className="mt-3">
        <button
          type="submit"
          disabled={!name.trim() || !isComplete(conditions, restrictions) || createRule.isPending}
          className="px-4 py-1.5 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover disabled:opacity-50 transition-colors"
        >
          {createRule.isPending ? "Creating..." : "Create"}
        </button>
      </div>
      {error && <p className="text-xs text-destructive mt-2">{error}</p>}
    </form>
  );
}

interface TicketRulesSectionProps {
  workspaceId: string;
  projectId: string;
}

export default function TicketRulesSection({ workspaceId, projectId }: TicketRulesSectionProps) {
  const rulesQuery = useTicketRules(projectId);
  const statusesQuery = useProjectStatuses(projectId);
  const labelsQuery = useLabels(workspaceId);

  const rules = rulesQuery.data ?? [];
  const statuses = statusesQuery.data ?? [];
  const labels = labelsQuery.data ?? [];

  return (
    <div className="mb-6">
      <h3 className="text-base font-semibold text-text-primary mb-1">Ticket Rules</h3>
      <p className="text-sm text-text-tertiary mb-4">
        Define conditions that restrict what can happen to a ticket — for example, prevent starting
        an AI session when a blocking ticket is not yet done, or block moving to a certain status.
      </p>

      {rulesQuery.isLoading ? (
        <div className="text-sm text-text-tertiary">Loading rules...</div>
      ) : (
        <div className="flex flex-col gap-2 mb-4">
          {rules.length === 0 && (
            <div className="text-sm text-text-tertiary p-3 bg-bg-surface border border-border-default rounded-lg">
              No ticket rules yet.
            </div>
          )}
          {rules.map((rule) => (
            <RuleRow
              key={rule.id}
              rule={rule}
              projectId={projectId}
              statuses={statuses}
              labels={labels}
            />
          ))}
        </div>
      )}

      <CreateRuleForm
        projectId={projectId}
        statuses={statuses}
        labels={labels}
      />
    </div>
  );
}
