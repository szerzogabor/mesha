"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { usePwa } from "@/context/PwaContext";
import { InstallAppButton } from "@/components/pwa/InstallAppButton";
import { QrCode } from "@/components/pwa/QrCode";

const iosSteps = [
  "Open this page in Safari on your iPhone or iPad.",
  "Tap the Share button (the square with an arrow).",
  'Scroll down and tap "Add to Home Screen".',
  'Tap "Add" — Mesha appears on your home screen.',
];

/**
 * Platform-gated hero copy + PWA promo. Rendered client-only (ssr: false at
 * the call site) since isAndroid/isIOS depend on navigator.userAgent, which
 * isn't available during SSR — gating it here avoids a hydration mismatch
 * between server HTML and the client's first render.
 */
export function DownloadHero() {
  const { isAndroid, isIOS, isStandalone } = usePwa();
  const [installUrl, setInstallUrl] = useState("https://mesha.app/download");

  useEffect(() => {
    setInstallUrl(`${window.location.origin}/download`);
  }, []);

  const isMobile = isAndroid || isIOS;
  // Android has a native app, so the PWA install path is only promoted on iOS
  // (no native app exists there) and on desktop (device unknown until scanned).
  const showPwaPromo = !isAndroid;

  return (
    <>
      <div className="flex flex-col items-center text-center mb-10">
        <h1 className="text-2xl md:text-3xl font-bold text-text-primary">
          {isAndroid ? "Get Mesha for Android" : "Install Mesha"}
        </h1>
        <p className="text-sm md:text-base text-text-secondary mt-2 max-w-md">
          {isAndroid
            ? "Download the native app above for on-device AI, voice input, and offline draft sync."
            : "Get the full Mesha experience on your phone — a fast, full-screen, installable app for managing AI agents, issues, and sessions on the go."}
        </p>

        {isAndroid ? (
          <Link
            href="/workspaces"
            className="mt-6 text-sm text-text-tertiary hover:text-text-primary transition-colors"
          >
            Continue in browser →
          </Link>
        ) : isStandalone ? (
          <div className="mt-6 px-4 py-3 rounded-lg bg-success-muted text-success text-sm font-medium">
            ✓ Mesha is installed — you&apos;re running the app.
          </div>
        ) : (
          <div className="mt-6 flex flex-col items-center gap-3">
            <InstallAppButton className="px-6 py-3 text-base" />
            <Link
              href="/workspaces"
              className="text-sm text-text-tertiary hover:text-text-primary transition-colors"
            >
              Continue in browser →
            </Link>
          </div>
        )}
      </div>

      {showPwaPromo && (
        <div
          className={
            isIOS ? "grid gap-6 md:grid-cols-2 md:items-start" : "max-w-md mx-auto"
          }
        >
          {/* Steps — iOS only; Android has the native app, desktop device is unknown */}
          {isIOS && (
            <section className="bg-bg-surface border border-border-default rounded-xl p-5">
              <h2 className="text-sm font-semibold text-text-primary mb-3">
                Add to Home Screen (iOS)
              </h2>
              <ol className="space-y-3">
                {iosSteps.map((step, i) => (
                  <li key={i} className="flex gap-3">
                    <span className="shrink-0 h-6 w-6 rounded-full bg-accent/10 text-accent text-xs font-semibold flex items-center justify-center">
                      {i + 1}
                    </span>
                    <span className="text-sm text-text-secondary leading-relaxed">
                      {step}
                    </span>
                  </li>
                ))}
              </ol>
            </section>
          )}

          {/* Desktop: QR to scan and continue setup on a phone. iOS: reassurance. */}
          <section className="bg-bg-surface border border-border-default rounded-xl p-5">
            {!isMobile ? (
              <>
                <h2 className="text-sm font-semibold text-text-primary mb-3">
                  Scan to continue on your phone
                </h2>
                <QrCode value={installUrl} />
                <p className="text-xs text-text-tertiary mt-3 text-center">
                  Scan with your phone&apos;s camera to open this page — Android
                  gets the native app, iPhone gets the install steps.
                </p>
              </>
            ) : (
              <>
                <h2 className="text-sm font-semibold text-text-primary mb-3">
                  Why install?
                </h2>
                <ul className="space-y-2 text-sm text-text-secondary">
                  <li>• Full-screen, app-like experience (no browser chrome)</li>
                  <li>• Launch straight from your home screen</li>
                  <li>• Offline-aware app shell</li>
                  <li>• Ready for push notifications (coming soon)</li>
                </ul>
              </>
            )}
          </section>
        </div>
      )}
    </>
  );
}
