import { UserButton } from "@clerk/nextjs";
import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { UserSync } from "@/components/auth/UserSync";

export default async function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { userId } = await auth();
  if (!userId) redirect("/sign-in");

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <UserSync />
      <header className="h-14 border-b bg-white flex items-center justify-between px-6 shrink-0">
        <span className="font-semibold text-lg tracking-tight">Mesha</span>
        <UserButton />
      </header>
      <div className="flex-1 overflow-auto">{children}</div>
    </div>
  );
}
