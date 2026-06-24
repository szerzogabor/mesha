"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@clerk/nextjs";
import { InstallAppButton } from "@/components/pwa/InstallAppButton";

export default function Home() {
  const router = useRouter();
  const { isLoaded, isSignedIn } = useAuth();

  // Signed-in users skip the marketing page and go straight to the app.
  useEffect(() => {
    if (isLoaded && isSignedIn) router.replace("/workspaces");
  }, [isLoaded, isSignedIn, router]);

  return (
    <div className="min-h-screen bg-bg-app">
      <main className="max-w-4xl mx-auto px-4 md:px-6 py-16 md:py-24">
        <div className="flex flex-col items-center text-center">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/icons/icon.svg"
            alt="Mesha"
            width={80}
            height={80}
            className="rounded-2xl shadow-sm mb-6"
          />
          <h1 className="text-3xl md:text-5xl font-bold text-text-primary tracking-tight">
            AI-native project management
          </h1>
          <p className="mt-4 text-base md:text-lg text-text-secondary max-w-xl">
            Create tickets from natural language, assign them to AI agents, and
            review the resulting pull requests — now on your phone.
          </p>

          {/* Hero CTA */}
          <div className="mt-8 flex flex-col sm:flex-row items-center gap-3">
            <Link
              href="/download"
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-accent px-6 py-3 text-base font-medium text-white hover:bg-accent-hover transition-colors min-h-[48px] w-full sm:w-auto"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 3v12" />
                <path d="m7 10 5 5 5-5" />
                <path d="M5 21h14" />
              </svg>
              Download the App
            </Link>
            <Link
              href="/sign-in"
              className="inline-flex items-center justify-center rounded-lg border border-border-strong px-6 py-3 text-base font-medium text-text-primary hover:bg-bg-surface-hover transition-colors min-h-[48px] w-full sm:w-auto"
            >
              Sign in
            </Link>
          </div>

          {/* Inline native install when available (Android Chrome) */}
          <div className="mt-4">
            <InstallAppButton
              label="Install on this device"
              className="bg-transparent text-accent hover:bg-accent-muted border border-accent/30"
            />
          </div>
        </div>

        {/* Feature highlights */}
        <div className="mt-16 grid gap-4 sm:grid-cols-3">
          {[
            {
              title: "Native Android app",
              body: "Install the Mesha APK on Android 13+ for the full, touch-optimized experience.",
            },
            {
              title: "On-device AI",
              body: "Draft issues with a local Gemma model — works offline, no cloud AI key required.",
            },
            {
              title: "Live AI sessions",
              body: "Follow real-time agent logs and send follow-ups from anywhere.",
            },
          ].map((f) => (
            <div
              key={f.title}
              className="bg-bg-surface border border-border-default rounded-xl p-5 text-left"
            >
              <h2 className="text-sm font-semibold text-text-primary">
                {f.title}
              </h2>
              <p className="text-sm text-text-tertiary mt-1.5">{f.body}</p>
            </div>
          ))}
        </div>
      </main>
    </div>
  );
}
