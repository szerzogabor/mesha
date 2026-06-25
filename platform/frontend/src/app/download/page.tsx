"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { usePwa } from "@/context/PwaContext";
import { InstallAppButton } from "@/components/pwa/InstallAppButton";
import { QrCode } from "@/components/pwa/QrCode";
import { APP_VERSION, RELEASE_NOTES } from "@/lib/app-version";
import {
  useLatestRelease,
  releaseDownloadUrl,
  formatBytes,
} from "@/hooks/useAppRelease";

const apkSteps = [
  "Tap the Download button to get the latest Mesha .apk.",
  'On first install, Android asks to allow "Install unknown apps" — enable it for your browser.',
  "Open the downloaded file and tap Install.",
  "Launch Mesha, sign in, and start creating issues with the on-device Gemma model.",
];

const iosSteps = [
  "Open this page in Safari on your iPhone or iPad.",
  "Tap the Share button (the square with an arrow).",
  'Scroll down and tap "Add to Home Screen".',
  'Tap "Add" — Mesha appears on your home screen.',
];

export default function DownloadPage() {
  const { isAndroid, isIOS, isStandalone } = usePwa();
  const [installUrl, setInstallUrl] = useState("https://mesha.app/download");
  const { data: release, isLoading: releaseLoading } = useLatestRelease("android");

  useEffect(() => {
    setInstallUrl(`${window.location.origin}/download`);
  }, []);

  const isMobile = isAndroid || isIOS;
  // Android has a native app, so the PWA install path is only promoted on iOS
  // (no native app exists there) and on desktop (device unknown until scanned).
  const showPwaPromo = !isAndroid;

  return (
    <div className="min-h-screen bg-bg-app">
      <main className="max-w-3xl mx-auto px-4 md:px-6 py-8 md:py-14">
        {/* Native Android app (APK) — the primary, full-featured client */}
        {!releaseLoading && release && (
          <section className="mb-10 bg-bg-surface border border-border-default rounded-xl p-5 md:p-6">
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-lg font-semibold text-text-primary">
                    Mesha for Android
                  </h2>
                  <span className="text-xs font-mono text-accent bg-accent/10 px-2 py-0.5 rounded">
                    v{release.versionName}
                  </span>
                </div>
                <p className="text-sm text-text-secondary mt-1 max-w-md">
                  The full native app with on-device AI issue creation (Gemma),
                  voice input, and offline draft sync. Requires Android 13+.
                </p>
                <p className="text-xs text-text-tertiary mt-2 font-mono">
                  {release.fileName} · {formatBytes(release.fileSize)} · SHA-256{" "}
                  {release.checksumSha256.slice(0, 12)}…
                </p>
              </div>
              <a
                href={releaseDownloadUrl(release)}
                className="inline-flex items-center justify-center gap-2 rounded-lg bg-accent px-6 py-3 text-base font-medium text-white hover:bg-accent-hover transition-colors min-h-[48px] shrink-0"
                download
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M12 3v12" />
                  <path d="m7 10 5 5 5-5" />
                  <path d="M5 21h14" />
                </svg>
                Download APK
              </a>
            </div>

            <ol className="mt-5 grid gap-3 sm:grid-cols-2">
              {apkSteps.map((step, i) => (
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
        <div className={isIOS ? "grid gap-6 md:grid-cols-2 md:items-start" : ""}>
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
