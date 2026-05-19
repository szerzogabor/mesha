"use client";

import { createContext, useContext } from "react";

export type AuthSyncStatus = "idle" | "syncing" | "ready";

export const AuthSyncContext = createContext<AuthSyncStatus>("idle");

export function useAuthSyncStatus() {
  return useContext(AuthSyncContext);
}
