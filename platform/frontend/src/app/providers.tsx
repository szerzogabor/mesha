"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import * as Sentry from "@sentry/nextjs";

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
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
