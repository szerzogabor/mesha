import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    const otlpTarget = process.env.NEXT_PUBLIC_OTEL_EXPORTER_OTLP_ENDPOINT?.replace(/\/$/, "");
    if (!otlpTarget) return [];
    return [
      {
        source: "/otlp/:path*",
        destination: `${otlpTarget}/:path*`,
      },
    ];
  },
};

export default nextConfig;
