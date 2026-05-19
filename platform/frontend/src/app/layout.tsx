import type { Metadata } from "next";
import { ClerkProvider, SignInButton, SignUpButton, Show, UserButton } from "@clerk/nextjs";
import "./globals.css";
import { Providers } from "./providers";

export const metadata: Metadata = {
  title: "Mesha — AI-Native Project Management",
  description: "AI-native project management platform with Blocks integration",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
        <ClerkProvider>
          <Show when="signed-out">
            <header className="flex gap-4 justify-end px-8 py-3 border-b bg-white">
              <SignInButton />
              <SignUpButton />
            </header>
          </Show>
          <Show when="signed-in">
            <header className="flex justify-end px-8 py-3 border-b bg-white">
              <UserButton />
            </header>
          </Show>
          <Providers>{children}</Providers>
        </ClerkProvider>
      </body>
    </html>
  );
}
