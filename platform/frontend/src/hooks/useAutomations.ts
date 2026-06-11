"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { AutomationAction, AutomationRule, AutomationTriggerType } from "@/types";

export function useAutomations(projectId: string) {
  return useQuery({
    queryKey: ["automations", projectId],
    queryFn: () => apiClient.get<AutomationRule[]>(`/api/projects/${projectId}/automations`),
    enabled: !!projectId,
  });
}

export function useCreateAutomation(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      triggerType: AutomationTriggerType;
      actions: AutomationAction[];
    }) => apiClient.post<AutomationRule>(`/api/projects/${projectId}/automations`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["automations", projectId] });
    },
  });
}

export function useUpdateAutomation(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      ruleId,
      data,
    }: {
      ruleId: string;
      data: {
        triggerType?: AutomationTriggerType;
        actions?: AutomationAction[];
        enabled?: boolean;
      };
    }) => apiClient.patch<AutomationRule>(`/api/projects/${projectId}/automations/${ruleId}`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["automations", projectId] });
    },
  });
}

export function useDeleteAutomation(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ruleId: string) =>
      apiClient.delete(`/api/projects/${projectId}/automations/${ruleId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["automations", projectId] });
    },
  });
}
