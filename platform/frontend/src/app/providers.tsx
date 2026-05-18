"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ClerkProvider, useAuth } from "@clerk/nextjs";
import { useEffect, useState } from "react";
import * as Sentry from "@sentry/nextjs";
import { setTokenGetter } from "@/lib/api-client";

function ClerkTokenBridge() {
  const { getToken } = useAuth();
  useEffect(() => {
    setTokenGetter(() => getToken());
  }, [getToken]);
  return null;
}

export function Providers({ children }: { children: React.ReactNode }) {
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
    <ClerkProvider>
      <ClerkTokenBridge />
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </ClerkProvider>
  );
}
