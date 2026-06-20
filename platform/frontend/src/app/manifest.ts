import type { MetadataRoute } from "next";

/**
 * Web App Manifest (served at /manifest.webmanifest).
 *
 * Drives the Android "Add to Home Screen" / installable PWA experience:
 * standalone display, brand theming, and maskable icons so the launcher icon
 * matches the device's icon shape.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Mesha — AI-Native Project Management",
    short_name: "Mesha",
    description:
      "Create tickets from natural language, assign them to AI agents, and review the resulting pull requests.",
    id: "/",
    start_url: "/workspaces",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#0f0f10",
    theme_color: "#5e6ad2",
    categories: ["productivity", "developer"],
    icons: [
      {
        src: "/icons/icon-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/icon-maskable-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "maskable",
      },
      {
        src: "/icons/icon-maskable-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
      {
        src: "/icons/icon.svg",
        sizes: "any",
        type: "image/svg+xml",
        purpose: "any",
      },
    ],
    shortcuts: [
      {
        name: "Workspaces",
        short_name: "Workspaces",
        url: "/workspaces",
      },
    ],
  };
}
