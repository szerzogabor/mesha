/**
 * Push notification foundation — shared types.
 *
 * This module defines the *architecture* for future push notifications. Actual
 * delivery (Firebase Cloud Messaging tokens, a backend `device_tokens` table,
 * and server-side send) is intentionally NOT implemented yet — see
 * `docs/MOBILE.md` → "Push Notifications" for the rollout plan.
 */

/** Domain events that will eventually be deliverable as push notifications. */
export type NotificationEventType =
  | "SESSION_COMPLETED"
  | "SESSION_FAILED"
  | "PULL_REQUEST_CREATED"
  | "AGENT_OFFLINE";

export interface NotificationPayload {
  type: NotificationEventType;
  title: string;
  body: string;
  /** Deep link opened when the notification is tapped. */
  url?: string;
  data?: Record<string, string>;
}

export type NotificationPermission = "default" | "granted" | "denied" | "unsupported";

/**
 * Abstraction over a push transport (FCM, Web Push, …). Business logic depends
 * on this interface only, mirroring the backend `ProviderAdapter` pattern, so
 * the concrete transport can be swapped without touching feature code.
 */
export interface NotificationProvider {
  readonly name: string;
  /** Whether this provider can run in the current environment. */
  isSupported(): boolean;
  /** Current browser permission state. */
  getPermission(): NotificationPermission;
  /** Prompt the user and return the resulting permission. */
  requestPermission(): Promise<NotificationPermission>;
  /**
   * Register this device for push and return an opaque device token to be
   * persisted server-side. Returns null when unavailable/denied.
   */
  registerDevice(): Promise<string | null>;
  /** Remove the device registration (e.g. on sign-out). */
  unregisterDevice(): Promise<void>;
}
