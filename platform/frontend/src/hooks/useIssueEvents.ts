"use client";

import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@clerk/nextjs";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/**
 * Subscribes to server-sent events for issue updates in a project.
 * Automatically invalidates React Query caches when issues are updated,
 * so the UI reflects changes without a manual refresh.
 */
export function useIssueEvents(projectId: string) {
  const queryClient = useQueryClient();
  const { getToken } = useAuth();
  const abortRef = useRef<AbortController | null>(null);
  const retryRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!projectId) return;

    let isMounted = true;

    async function connect() {
      if (!isMounted) return;

      const token = await getToken();
      if (!token || !isMounted) return;

      abortRef.current?.abort();
      abortRef.current = new AbortController();

      try {
        const res = await fetch(
          `${API_BASE_URL}/api/projects/${projectId}/issues/stream`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
              Accept: "text/event-stream",
              "Cache-Control": "no-cache",
            },
            signal: abortRef.current.signal,
          }
        );

        if (!res.ok || !res.body) {
          scheduleReconnect();
          return;
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        let currentEventName = "";

        while (isMounted) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() ?? "";

          for (const line of lines) {
            if (line.startsWith("event:")) {
              currentEventName = line.slice(6).trim();
            } else if (line.startsWith("data:")) {
              if (currentEventName === "issue-updated") {
                try {
                  const data = JSON.parse(line.slice(5).trim());
                  queryClient.invalidateQueries({ queryKey: ["issues", projectId] });
                  queryClient.invalidateQueries({ queryKey: ["issues-all", projectId] });
                  if (data?.id) {
                    queryClient.invalidateQueries({ queryKey: ["issue", data.id] });
                  }
                } catch {
                  // ignore malformed event data
                }
              }
              currentEventName = "";
            } else if (line === "") {
              currentEventName = "";
            }
          }
        }

        if (isMounted) scheduleReconnect();
      } catch (e) {
        if (isMounted) scheduleReconnect();
      }
    }

    function scheduleReconnect() {
      if (!isMounted) return;
      retryRef.current = setTimeout(connect, 5000);
    }

    connect();

    return () => {
      isMounted = false;
      if (retryRef.current) clearTimeout(retryRef.current);
      abortRef.current?.abort();
    };
  }, [projectId, queryClient, getToken]);
}
