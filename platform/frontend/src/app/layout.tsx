import type { Metadata } from "next";
import "./globals.css";
import { ClerkProvider } from "@clerk/nextjs";
import { Providers } from "./providers";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Mesha — AI-Native Project Management",
  description: "AI-native project management platform with Blocks integration",
};

// A real Clerk publishable key looks like pk_test_<base64url≥30chars> or pk_live_...
const clerkKey = process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY ?? "";
const isClerkConfigured = /^pk_(test|live)_[A-Za-z0-9_-]{30,}$/.test(clerkKey);

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  if (!isClerkConfigured) {
    return (
      <html lang="en">
        <body
          style={{
            fontFamily: "system-ui, sans-serif",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            minHeight: "100vh",
            margin: 0,
          }}
        >
          <div style={{ maxWidth: 480, textAlign: "center", padding: "2rem" }}>
            <h1 style={{ fontSize: "1.5rem", fontWeight: 700, marginBottom: "1rem" }}>
              Authentication not configured
            </h1>
            <p style={{ color: "#555", marginBottom: "1rem" }}>
              Set these environment variables to enable authentication:
            </p>
            <code
              style={{
                display: "block",
                background: "#f5f5f5",
                padding: "1rem",
                borderRadius: "0.5rem",
                textAlign: "left",
                fontSize: "0.875rem",
                lineHeight: 2,
              }}
            >
              NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY
              <br />
              CLERK_SECRET_KEY
            </code>
          </div>
        </body>
      </html>
    );
  }

  return (
    <ClerkProvider afterSignOutUrl="/">
      <html lang="en" suppressHydrationWarning>
        <body>
          <Providers>{children}</Providers>
        </body>
      </html>
    </ClerkProvider>
  );
}
