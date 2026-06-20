"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { logger } from "@/lib/logger";

/** The non-standard `beforeinstallprompt` event (Chromium/Android). */
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

interface PwaContextValue {
  /** A deferred install prompt is available (Android Chrome, not yet installed). */
  canInstall: boolean;
  /** App is running in standalone/installed mode. */
  isStandalone: boolean;
  isAndroid: boolean;
  isIOS: boolean;
  /** Trigger the native install prompt. Resolves to whether the user accepted. */
  promptInstall: () => Promise<boolean>;
}

const PwaContext = createContext<PwaContextValue>({
  canInstall: false,
  isStandalone: false,
  isAndroid: false,
  isIOS: false,
  promptInstall: async () => false,
});

export function usePwa(): PwaContextValue {
  return useContext(PwaContext);
}

export function PwaProvider({ children }: { children: React.ReactNode }) {
  const [deferredPrompt, setDeferredPrompt] =
    useState<BeforeInstallPromptEvent | null>(null);
  const [isStandalone, setIsStandalone] = useState(false);
  const [platform, setPlatform] = useState<{ android: boolean; ios: boolean }>({
    android: false,
    ios: false,
  });

  // Register the service worker for offline app-shell support.
  useEffect(() => {
    if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) {
      return;
    }
    const register = () => {
      navigator.serviceWorker
        .register("/sw.js")
        .then(() => logger.info("Service worker registered", { source: "pwa" }))
        .catch((error) =>
          logger.error(
            "Service worker registration failed",
            error instanceof Error ? error : undefined,
            { source: "pwa" }
          )
        );
    };
    if (document.readyState === "complete") register();
    else window.addEventListener("load", register, { once: true });
  }, []);

  // Detect platform + standalone display mode.
  useEffect(() => {
    const ua = navigator.userAgent || "";
    setPlatform({
      android: /android/i.test(ua),
      ios: /iphone|ipad|ipod/i.test(ua),
    });

    const mql = window.matchMedia("(display-mode: standalone)");
    const update = () =>
      setIsStandalone(
        mql.matches ||
          // iOS Safari exposes standalone via navigator.standalone
          (navigator as Navigator & { standalone?: boolean }).standalone === true
      );
    update();
    mql.addEventListener("change", update);
    return () => mql.removeEventListener("change", update);
  }, []);

  // Capture the deferred install prompt and clear it once installed.
  useEffect(() => {
    const onBeforeInstall = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
      logger.info("Install prompt available", { source: "pwa" });
    };
    const onInstalled = () => {
      setDeferredPrompt(null);
      logger.info("PWA installed", { source: "pwa" });
    };
    window.addEventListener("beforeinstallprompt", onBeforeInstall);
    window.addEventListener("appinstalled", onInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onBeforeInstall);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, []);

  const promptInstall = useCallback(async () => {
    if (!deferredPrompt) return false;
    await deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    setDeferredPrompt(null);
    logger.info("Install prompt resolved", { source: "pwa", outcome });
    return outcome === "accepted";
  }, [deferredPrompt]);

  const value = useMemo<PwaContextValue>(
    () => ({
      canInstall: !!deferredPrompt && !isStandalone,
      isStandalone,
      isAndroid: platform.android,
      isIOS: platform.ios,
      promptInstall,
    }),
    [deferredPrompt, isStandalone, platform, promptInstall]
  );

  return <PwaContext.Provider value={value}>{children}</PwaContext.Provider>;
}
