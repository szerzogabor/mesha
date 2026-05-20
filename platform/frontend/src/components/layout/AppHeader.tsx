"use client";

import { SignInButton, SignUpButton, Show, UserButton } from "@clerk/nextjs";
import { usePathname } from "next/navigation";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

export function AppHeader() {
  const pathname = usePathname();

  // Nested workspace routes render their own top bars.
  if (/^\/workspaces\/[^/]+/.test(pathname)) {
    return null;
  }

  return (
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
  );
}
