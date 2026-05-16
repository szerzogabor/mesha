"use client";

import { useUser } from "@clerk/nextjs";
import { useAuthenticatedApi } from "@/hooks/useAuthenticatedApi";
import { useEffect, useRef } from "react";

/**
 * Upserts the authenticated Clerk user into our backend database.
 * Mount this once inside any authenticated layout.
 */
export function UserSync() {
  const { user, isLoaded } = useUser();
  const api = useAuthenticatedApi();
  const synced = useRef(false);

  useEffect(() => {
    if (!isLoaded || !user || synced.current) return;
    synced.current = true;

    const email = user.primaryEmailAddress?.emailAddress ?? "";
    const name = user.fullName ?? user.username ?? undefined;

    api
      .post("/api/auth/sync", { email, name })
      .catch((err) => console.error("User sync failed:", err));
  }, [isLoaded, user, api]);

  return null;
}
