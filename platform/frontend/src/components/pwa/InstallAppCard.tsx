"use client";

import { useEffect, useState } from "react";
import { usePwa } from "@/context/PwaContext";
import { InstallAppButton } from "./InstallAppButton";

const DISMISS_KEY = "mesha-install-card-dismissed";

/**
 * A lightweight, dismissible "install Mesha" banner for the dashboard.
 * Only shown to mobile web users who can install (Android Chrome) and haven't
 * dismissed it. Hidden entirely in standalone/installed mode and on desktop.
 */
export function InstallAppCard() {
  const { canInstall, isStandalone, isAndroid } = usePwa();
  const [dismissed, setDismissed] = useState(true);

  useEffect(() => {
    setDismissed(localStorage.getItem(DISMISS_KEY) === "true");
  }, []);

  if (isStandalone || dismissed || (!canInstall && !isAndroid)) return null;

  return (
    <div className="relative flex flex-col sm:flex-row sm:items-center gap-3 bg-accent-muted border border-accent/30 rounded-xl p-4">
      <button
        type="button"
        aria-label="Dismiss"
        onClick={() => {
          localStorage.setItem(DISMISS_KEY, "true");
          setDismissed(true);
        }}
        className="absolute top-2 right-2 text-accent-muted-text/70 hover:text-accent-muted-text text-lg leading-none"
      >
        ×
      </button>
      <div className="flex-1 min-w-0 pr-6">
        <p className="text-sm font-semibold text-accent-muted-text">
          Install Mesha on your phone
        </p>
        <p className="text-xs text-accent-muted-text/80 mt-0.5">
          Add Mesha to your home screen for a full-screen, app-like experience.
        </p>
      </div>
      <InstallAppButton className="shrink-0" />
    </div>
  );
}
