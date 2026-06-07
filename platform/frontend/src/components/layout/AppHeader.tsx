"use client";

import { SignInButton, SignUpButton, Show, UserButton } from "@clerk/nextjs";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

export function AppHeader() {
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
