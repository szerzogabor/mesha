import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Workspace } from "@/types";

interface WorkspaceState {
  currentWorkspace: Workspace | null;
  setCurrentWorkspace: (ws: Workspace | null) => void;
}

export const useWorkspaceStore = create<WorkspaceState>()(
  persist(
    (set) => ({
      currentWorkspace: null,
      setCurrentWorkspace: (ws) => set({ currentWorkspace: ws }),
    }),
    { name: "mesha-workspace" }
  )
);
