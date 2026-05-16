import { type NextFetchEvent, type NextRequest, NextResponse } from "next/server";
import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";

const isPublicRoute = createRouteMatcher([
  "/",
  "/sign-in(.*)",
  "/sign-up(.*)",
]);

const clerk = clerkMiddleware(async (auth, req) => {
  if (!isPublicRoute(req)) {
    await auth.protect();
  }
});

export default async function middleware(req: NextRequest, event: NextFetchEvent) {
  try {
    return await clerk(req, event);
  } catch (err) {
    // Clerk is misconfigured (missing or invalid publishable key).
    // Allow public routes through; redirect protected routes to sign-in so the
    // app stays partially usable rather than returning a 500 on every request.
    console.error("[middleware] Clerk error:", err);
    if (!isPublicRoute(req)) {
      return NextResponse.redirect(new URL("/sign-in", req.url));
    }
    return NextResponse.next();
  }
}

export const config = {
  matcher: [
    "/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
    "/(api|trpc)(.*)",
  ],
};
