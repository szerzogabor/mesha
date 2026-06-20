"use client";

import Link from "next/link";
import { useState } from "react";
import { usePwa } from "@/context/PwaContext";
import { cn } from "@/lib/utils";

const DownloadIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 3v12" />
    <path d="m7 10 5 5 5-5" />
    <path d="M5 21h14" />
  </svg>
);

interface InstallAppButtonProps {
  className?: string;
  /** Label when a native install prompt is available. */
  label?: string;
}

/**
 * Smart install affordance:
 * - When Chrome's install prompt is available → triggers it natively.
 * - Otherwise → links to /download for manual instructions (iOS, desktop, or
 *   browsers that haven't fired `beforeinstallprompt` yet).
 * Renders nothing once the app is already installed/standalone.
 */
export function InstallAppButton({
  className,
  label = "Install app",
}: InstallAppButtonProps) {
  const { canInstall, isStandalone, promptInstall } = usePwa();
  const [busy, setBusy] = useState(false);

  if (isStandalone) return null;

  const classes = cn(
    "inline-flex items-center justify-center gap-2 rounded-lg bg-accent px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-accent-hover min-h-[44px]",
    className
  );

  if (canInstall) {
    return (
      <button
        type="button"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          try {
            await promptInstall();
          } finally {
            setBusy(false);
          }
        }}
        className={classes}
      >
        <DownloadIcon />
        {busy ? "Installing…" : label}
      </button>
    );
  }

  return (
    <Link href="/download" className={classes}>
      <DownloadIcon />
      {label}
    </Link>
  );
}
