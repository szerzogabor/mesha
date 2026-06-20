"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { usePwa } from "@/context/PwaContext";
import { InstallAppButton } from "@/components/pwa/InstallAppButton";
import { QrCode } from "@/components/pwa/QrCode";
import { APP_VERSION, RELEASE_NOTES } from "@/lib/app-version";

const androidSteps = [
  "Open this page in Chrome on your Android phone.",
  'Tap the "Install app" button below, or open Chrome\'s ⋮ menu.',
  'Choose "Install app" / "Add to Home screen".',
  "Confirm — Mesha appears on your home screen and opens full-screen.",
];

const iosSteps = [
  "Open this page in Safari on your iPhone or iPad.",
  "Tap the Share button (the square with an arrow).",
  'Scroll down and tap "Add to Home Screen".',
  'Tap "Add" — Mesha appears on your home screen.',
];

export default function DownloadPage() {
  const { isAndroid, isIOS, isStandalone } = usePwa();
  const [installUrl, setInstallUrl] = useState("https://mesha.app/workspaces");

  useEffect(() => {
    setInstallUrl(`${window.location.origin}/workspaces`);
  }, []);

  const isMobile = isAndroid || isIOS;
  const steps = isIOS ? iosSteps : androidSteps;

  return (
    <div className="min-h-screen bg-bg-app">
      <main className="max-w-3xl mx-auto px-4 md:px-6 py-8 md:py-14">
        {/* Hero */}
        <div className="flex flex-col items-center text-center mb-10">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/icons/icon.svg"
            alt="Mesha"
            width={72}
            height={72}
            className="rounded-2xl shadow-sm mb-5"
          />
          <h1 className="text-2xl md:text-3xl font-bold text-text-primary">
            Install Mesha on Android
          </h1>
          <p className="text-sm md:text-base text-text-secondary mt-2 max-w-md">
            Get the full Mesha experience on your phone — a fast, full-screen,
            installable app for managing AI agents, issues, and sessions on the go.
          </p>

          {isStandalone ? (
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

        <div className="grid gap-6 md:grid-cols-2 md:items-start">
          {/* Steps */}
          <section className="bg-bg-surface border border-border-default rounded-xl p-5">
            <h2 className="text-sm font-semibold text-text-primary mb-3">
              {isIOS ? "Add to Home Screen (iOS)" : "Install on Android"}
            </h2>
            <ol className="space-y-3">
              {steps.map((step, i) => (
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

          {/* Desktop: QR to scan. Mobile: reassurance + version. */}
          <section className="bg-bg-surface border border-border-default rounded-xl p-5">
            {!isMobile ? (
              <>
                <h2 className="text-sm font-semibold text-text-primary mb-3">
                  Scan to install on your phone
                </h2>
                <QrCode value={installUrl} />
                <p className="text-xs text-text-tertiary mt-3 text-center">
                  Scan with your Android camera, then follow the install prompt
                  in Chrome.
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

        {/* Version + release notes */}
        <section className="mt-8">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-semibold text-text-primary">
              Release notes
            </h2>
            <span className="text-xs font-mono text-text-tertiary">
              v{APP_VERSION}
            </span>
          </div>
          <div className="space-y-4">
            {RELEASE_NOTES.map((note) => (
              <div
                key={note.version}
                className="bg-bg-surface border border-border-default rounded-xl p-4"
              >
                <div className="flex items-baseline gap-2 mb-2">
                  <span className="text-sm font-semibold text-text-primary">
                    v{note.version}
                  </span>
                  <span className="text-xs text-text-tertiary">{note.date}</span>
                </div>
                <ul className="space-y-1">
                  {note.highlights.map((h, i) => (
                    <li key={i} className="text-sm text-text-secondary flex gap-2">
                      <span className="text-accent">•</span>
                      <span>{h}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
