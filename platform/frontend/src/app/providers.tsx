"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useAuth, useUser } from "@clerk/nextjs";
import { useEffect, useRef, useState } from "react";
import * as Sentry from "@sentry/nextjs";
import { apiClient, setTokenGetter } from "@/lib/api-client";
import { AuthSyncContext, AuthSyncStatus } from "@/lib/auth-sync";
import { ThemeProvider } from "@/lib/theme";

function ClerkTokenBridge({ onStatusChange }: { onStatusChange: (status: AuthSyncStatus) => void }) {
  const { getToken } = useAuth();
  const { user, isLoaded } = useUser();
  const syncedUserIdRef = useRef<string | null>(null);

  useEffect(() => {
    setTokenGetter(() => getToken());
  }, [getToken]);

  useEffect(() => {
    if (!isLoaded || !user) {
      syncedUserIdRef.current = null;
      onStatusChange("idle");
      return;
    }

    if (syncedUserIdRef.current === user.id) {
      onStatusChange("ready");
      return;
    }

    const primaryEmail = user.primaryEmailAddress?.emailAddress;
    if (!primaryEmail) {
      Sentry.logger.warn("Skipping user sync because primary email is missing", {
        userId: user.id,
      });
      onStatusChange("ready");
      return;
    }

    onStatusChange("syncing");

    apiClient
      .post("/api/auth/sync", {
        email: primaryEmail,
        name: user.fullName,
      })
      .then(() => {
        syncedUserIdRef.current = user.id;
        onStatusChange("ready");
      })
      .catch((error) => {
        onStatusChange("ready");
        Sentry.captureException(error, {
          tags: { source: "auth-sync" },
          extra: { userId: user.id },
        });
      });
  }, [isLoaded, onStatusChange, user]);

  return null;
}

export function Providers({ children }: { children: React.ReactNode }) {
  const [authSyncStatus, setAuthSyncStatus] = useState<AuthSyncStatus>("idle");
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60 * 1000,
          },
          mutations: {
            onError(error) {
              Sentry.logger.error("React Query mutation error", {
                source: "react-query-mutation",
                errorMessage: error instanceof Error ? error.message : String(error),
              });
              Sentry.captureException(error, { tags: { source: "react-query-mutation" } });
            },
          },
        },
      })
  );

  return (
    <ThemeProvider>
      <AuthSyncContext.Provider value={authSyncStatus}>
        <ClerkTokenBridge onStatusChange={setAuthSyncStatus} />
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </AuthSyncContext.Provider>
    </ThemeProvider>
  );
}
