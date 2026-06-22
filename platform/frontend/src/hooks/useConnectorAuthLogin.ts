"use client";

import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { ConnectorTokenResponse } from "@/types";

export function useConnectorAuthLogin() {
  return useMutation({
    mutationFn: async () => {
      return apiClient.post<ConnectorTokenResponse>("/api/connector/auth/login", {});
    },
  });
}
