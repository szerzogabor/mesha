"use client";

import { Suspense, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Spinner } from "@/components/ui/Spinner";

function GitHubCallbackHandler() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const installationId = searchParams.get("installation_id");
    const setupAction = searchParams.get("setup_action");
    const workspaceId = searchParams.get("state");

    if (!workspaceId || !installationId) {
      router.replace("/workspaces");
      return;
    }

    const params = new URLSearchParams({ installation_id: installationId });
    if (setupAction) params.set("setup_action", setupAction);

    router.replace(`/workspaces/${workspaceId}/github?${params.toString()}`);
  }, [router, searchParams]);

  return (
    <div className="flex items-center justify-center h-screen">
      <Spinner size="lg" />
    </div>
  );
}

export default function GitHubCallbackPage() {
  return (
    <Suspense
      fallback={
        <div className="flex items-center justify-center h-screen">
          <Spinner size="lg" />
        </div>
      }
    >
      <GitHubCallbackHandler />
    </Suspense>
  );
}
