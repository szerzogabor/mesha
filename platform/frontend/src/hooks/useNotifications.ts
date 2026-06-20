"use client";

import { useCallback, useEffect, useState } from "react";
import {
  getNotificationProvider,
  readBrowserPermission,
} from "@/lib/notifications/provider";
import { NotificationPermission } from "@/lib/notifications/types";

/**
 * React entry point for the push-notification foundation.
 *
 * Exposes the current permission state and a `enable()` action that requests
 * permission and registers the device through the active NotificationProvider.
 * Until FCM is wired up the provider is a no-op, so `enable()` resolves without
 * delivering anything — the UI/permission plumbing is ready for that future work.
 */
export function useNotifications() {
  const [permission, setPermission] = useState<NotificationPermission>("default");
  const [registering, setRegistering] = useState(false);

  useEffect(() => {
    setPermission(readBrowserPermission());
  }, []);

  const provider = getNotificationProvider();
  const isSupported = permission !== "unsupported";

  const enable = useCallback(async () => {
    setRegistering(true);
    try {
      const result = await provider.requestPermission();
      setPermission(result);
      if (result === "granted") {
        await provider.registerDevice();
      }
      return result;
    } finally {
      setRegistering(false);
    }
  }, [provider]);

  return {
    permission,
    isSupported,
    isEnabled: permission === "granted",
    registering,
    enable,
  };
}
