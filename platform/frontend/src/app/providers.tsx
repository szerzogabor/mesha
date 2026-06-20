"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useAuth, useUser } from "@clerk/nextjs";
import { useEffect, useRef, useState } from "react";
import { apiClient, setTokenGetter } from "@/lib/api-client";
import { AuthSyncContext, AuthSyncStatus } from "@/lib/auth-sync";
import { ThemeProvider } from "@/context/ThemeContext";
import { PwaProvider } from "@/context/PwaContext";
import { logger } from "@/lib/logger";

function ClerkTokenBridge({ onStatusChange }: { onStatusChange: (status: AuthSyncStatus) => void }) {
  const { getToken } = useAuth();
  const { user, isLoaded } = useUser();
  const syncedUserIdRef = useRef<string | null>(null);
  const prevStatusRef = useRef<AuthSyncStatus>("idle");

  const changeStatus = (next: AuthSyncStatus) => {
    const prev = prevStatusRef.current;
    if (prev !== next) {
      logger.auth.stateChange(prev, next, { userId: user?.id });
      prevStatusRef.current = next;
    }
    onStatusChange(next);
  };

  useEffect(() => {
    setTokenGetter(() => getToken());
  }, [getToken]);

  useEffect(() => {
    if (!isLoaded || !user) {
      syncedUserIdRef.current = null;
      changeStatus("idle");
      return;
    }

    if (syncedUserIdRef.current === user.id) {
      changeStatus("ready");
      return;
    }

    const primaryEmail = user.primaryEmailAddress?.emailAddress;
    if (!primaryEmail) {
      logger.auth.syncSkipped("primary email missing", { userId: user.id });
      changeStatus("ready");
      return;
    }

    changeStatus("syncing");
    logger.auth.syncStarted(user.id);

    apiClient
      .post("/api/auth/sync", {
        email: primaryEmail,
        name: user.fullName,
      })
      .then(() => {
        syncedUserIdRef.current = user.id;
        logger.auth.syncCompleted(user.id);
        changeStatus("ready");
      })
      .catch((error) => {
        logger.auth.syncFailed(error, { userId: user.id });
        changeStatus("ready");
      });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoaded, user]);

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
              logger.error("React Query mutation error", error, {
                source: "react-query-mutation",
              });
            },
          },
        },
      })
  );

  return (
    <ThemeProvider>
      <PwaProvider>
        <AuthSyncContext.Provider value={authSyncStatus}>
          <ClerkTokenBridge onStatusChange={setAuthSyncStatus} />
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        </AuthSyncContext.Provider>
      </PwaProvider>
    </ThemeProvider>
  );
}
