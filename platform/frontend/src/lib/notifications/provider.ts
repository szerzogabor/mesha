"use client";

import { logger } from "@/lib/logger";
import {
  NotificationPermission,
  NotificationProvider,
} from "./types";

/**
 * Reads the current browser Notification permission, normalized to our enum.
 */
export function readBrowserPermission(): NotificationPermission {
  if (typeof window === "undefined" || !("Notification" in window)) {
    return "unsupported";
  }
  return Notification.permission as NotificationPermission;
}

/**
 * No-op provider used as the default until a real transport (FCM) is wired up.
 * It tracks browser permission so UI can already reflect notification state,
 * but never registers a device or delivers anything.
 */
export class NoopNotificationProvider implements NotificationProvider {
  readonly name = "noop";

  isSupported(): boolean {
    return typeof window !== "undefined" && "Notification" in window;
  }

  getPermission(): NotificationPermission {
    return readBrowserPermission();
  }

  async requestPermission(): Promise<NotificationPermission> {
    if (!this.isSupported()) return "unsupported";
    const result = await Notification.requestPermission();
    logger.info("Notification permission requested", {
      source: "notifications",
      result,
    });
    return result as NotificationPermission;
  }

  async registerDevice(): Promise<string | null> {
    // Foundation only: a real implementation would obtain an FCM token here and
    // POST it to the backend (e.g. /api/notifications/devices).
    logger.info("Device registration skipped (noop provider)", {
      source: "notifications",
    });
    return null;
  }

  async unregisterDevice(): Promise<void> {
    /* no-op */
  }
}

/*
 * FUTURE: FirebaseCloudMessagingProvider
 * --------------------------------------
 * Implements NotificationProvider using firebase/messaging + a dedicated
 * firebase-messaging-sw.js service worker. Outline:
 *
 *   import { getMessaging, getToken, onMessage } from "firebase/messaging";
 *
 *   class FirebaseCloudMessagingProvider implements NotificationProvider {
 *     async registerDevice() {
 *       const token = await getToken(messaging, { vapidKey: VAPID_KEY });
 *       await apiClient.post("/api/notifications/devices", { token, platform: "web" });
 *       return token;
 *     }
 *   }
 *
 * Requires: NEXT_PUBLIC_FIREBASE_* config, a VAPID key, the `firebase` dep, and
 * backend endpoints to store tokens + fan out SESSION_COMPLETED / SESSION_FAILED
 * / PULL_REQUEST_CREATED / AGENT_OFFLINE events. Tracked in docs/MOBILE.md.
 */

let activeProvider: NotificationProvider = new NoopNotificationProvider();

/** Returns the currently configured notification provider. */
export function getNotificationProvider(): NotificationProvider {
  return activeProvider;
}

/** Swap the active provider (e.g. once FCM is configured). */
export function setNotificationProvider(provider: NotificationProvider): void {
  activeProvider = provider;
}
