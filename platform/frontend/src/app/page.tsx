import Link from "next/link";
import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";

export default async function Home() {
  const { userId } = await auth();
  if (userId) redirect("/dashboard");

  return (
    <main className="min-h-screen flex flex-col items-center justify-center p-8 bg-white">
      <h1 className="text-5xl font-bold mb-4 tracking-tight">Mesha</h1>
      <p className="text-xl text-gray-500 mb-10">
        AI-native project management platform
      </p>
      <div className="flex gap-4">
        <Link
          href="/sign-in"
          className="px-6 py-3 rounded-lg bg-black text-white font-medium hover:bg-gray-800 transition-colors"
        >
          Sign in
        </Link>
        <Link
          href="/sign-up"
          className="px-6 py-3 rounded-lg border border-gray-300 text-gray-700 font-medium hover:bg-gray-50 transition-colors"
        >
          Get started
        </Link>
      </div>
    </main>
  );
}
