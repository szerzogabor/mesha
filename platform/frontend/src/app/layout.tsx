import type { Metadata } from "next";
import { ClerkProvider, SignInButton, SignUpButton, Show, UserButton } from "@clerk/nextjs";
import "./globals.css";
import { Providers } from "./providers";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

export const metadata: Metadata = {
  title: "Mesha — AI-Native Project Management",
  description: "AI-native project management platform with Blocks integration",
};

// Injected before React hydration to prevent flash of wrong theme
const themeScript = `
(function() {
  try {
    var stored = localStorage.getItem('mesha-theme');
    var mode = stored && ['light','dark','system'].includes(stored) ? stored : 'system';
    var resolved = mode === 'system'
      ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : mode;
    if (resolved === 'dark') document.documentElement.classList.add('dark');
  } catch (e) {}
})();
`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="bg-bg-app text-text-primary">
        <ClerkProvider>
          <Providers>
            <header className="flex gap-4 justify-end items-center px-8 py-3 border-b border-border-default bg-bg-surface">
              <ThemeToggle />
              <Show when="signed-out">
                <SignInButton />
                <SignUpButton />
              </Show>
              <Show when="signed-in">
                <UserButton />
              </Show>
            </header>
            {children}
          </Providers>
        </ClerkProvider>
      </body>
    </html>
  );
}
