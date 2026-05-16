"use client";

import { useAuth } from "@clerk/nextjs";
import { useMemo } from "react";
import { createApiClient } from "@/lib/api-client";

export function useAuthenticatedApi() {
  const { getToken } = useAuth();

  return useMemo(
    () => ({
      async get<T>(path: string): Promise<T> {
        const token = await getToken();
        return createApiClient(token).get<T>(path);
      },
      async post<T>(path: string, body: unknown): Promise<T> {
        const token = await getToken();
        return createApiClient(token).post<T>(path, body);
      },
      async patch<T>(path: string, body: unknown): Promise<T> {
        const token = await getToken();
        return createApiClient(token).patch<T>(path, body);
      },
      async delete(path: string): Promise<void> {
        const token = await getToken();
        return createApiClient(token).delete(path);
      },
    }),
    [getToken]
  );
}
