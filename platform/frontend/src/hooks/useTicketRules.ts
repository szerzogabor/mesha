"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { TicketRule, TicketRuleConditionType, TicketRuleRestrictionType } from "@/types";

export interface TicketRuleConditionRequest {
  conditionType: TicketRuleConditionType;
  conditionValue: string;
}

export interface TicketRuleRestrictionRequest {
  restrictionType: TicketRuleRestrictionType;
  restrictionValue?: string;
}

export function useTicketRules(projectId: string) {
  return useQuery({
    queryKey: ["ticketRules", projectId],
    queryFn: () => apiClient.get<TicketRule[]>(`/api/projects/${projectId}/ticket-rules`),
    enabled: !!projectId,
  });
}

export function useCreateTicketRule(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      name: string;
      conditions: TicketRuleConditionRequest[];
      restrictions: TicketRuleRestrictionRequest[];
    }) => apiClient.post<TicketRule>(`/api/projects/${projectId}/ticket-rules`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ticketRules", projectId] });
    },
  });
}

export function useUpdateTicketRule(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      ruleId,
      data,
    }: {
      ruleId: string;
      data: {
        conditions?: TicketRuleConditionRequest[];
        restrictions?: TicketRuleRestrictionRequest[];
        enabled?: boolean;
      };
    }) => apiClient.patch<TicketRule>(`/api/projects/${projectId}/ticket-rules/${ruleId}`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ticketRules", projectId] });
    },
  });
}

export function useDeleteTicketRule(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ruleId: string) =>
      apiClient.delete(`/api/projects/${projectId}/ticket-rules/${ruleId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ticketRules", projectId] });
    },
  });
}
