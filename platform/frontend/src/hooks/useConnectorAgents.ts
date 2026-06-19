"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { ConnectorAgent } from "@/types";

export function useConnectorAgents() {
  return useQuery({
    queryKey: ["connectorAgents"],
    queryFn: () => apiClient.get<ConnectorAgent[]>("/api/agents"),
    refetchInterval: 15000,
  });
}
