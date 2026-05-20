"use client";

import { SignInButton, SignUpButton, Show, UserButton } from "@clerk/nextjs";
import { usePathname } from "next/navigation";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

export function AppHeader() {
  const pathname = usePathname();

  // Workspace pages manage their own in-content top bars.
  if (pathname === "/workspaces" || pathname.startsWith("/workspaces/")) {
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
