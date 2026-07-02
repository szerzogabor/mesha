"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { AppRelease } from "@/types";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/**
 * Latest published native client release for a platform. Public endpoint — works
 * without an authenticated session, so this powers the anonymous /download page.
 * A 404 (no release uploaded yet) resolves to `null` rather than throwing so the
 * page can gracefully fall back to the installable PWA.
 */
export function useLatestRelease(platform: string = "android") {
  return useQuery({
    queryKey: ["appRelease", platform, "latest"],
    queryFn: async () => {
      try {
        return await apiClient.get<AppRelease>(
          `/api/releases/${platform}/latest`,
        );
      } catch (err) {
        if ((err as { status?: number })?.status === 404) return null;
        throw err;
      }
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}

/** All published releases for a platform, newest first (release-notes history). */
export function useReleaseHistory(platform: string = "android") {
  return useQuery({
    queryKey: ["appRelease", platform, "history"],
    queryFn: async () => {
      try {
        return await apiClient.get<AppRelease[]>(`/api/releases/${platform}`);
      } catch (err) {
        if ((err as { status?: number })?.status === 404) return [];
        throw err;
      }
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}

/**
 * Absolute URL to a release's APK, suitable for a plain anchor download. The backend
 * returns an absolute URL pointing at externally-hosted release binary (a GitHub
 * Release asset), so only prepend the API base when it hasn't already given us one.
 */
export function releaseDownloadUrl(release: AppRelease): string {
  return release.downloadUrl.startsWith("http")
    ? release.downloadUrl
    : `${API_BASE_URL}${release.downloadUrl}`;
}

export function formatBytes(bytes: number): string {
  if (!bytes) return "—";
  const mb = bytes / (1024 * 1024);
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(bytes / 1024).toFixed(0)} KB`;
}
