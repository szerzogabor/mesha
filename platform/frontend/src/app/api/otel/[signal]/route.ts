import { NextRequest, NextResponse } from "next/server";

// Server-side env vars — never exposed to the browser bundle.
const GRAFANA_ENDPOINT = process.env.OTEL_EXPORTER_OTLP_ENDPOINT ?? "";
const GRAFANA_AUTH = process.env.OTEL_EXPORTER_OTLP_AUTH ?? "";

// Supported OTLP signals forwarded by this proxy.
const ALLOWED_SIGNALS = new Set(["traces", "logs"]);

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ signal: string }> }
) {
  const { signal } = await params;

  if (!ALLOWED_SIGNALS.has(signal)) {
    return NextResponse.json({ error: "Unknown signal" }, { status: 404 });
  }

  // Silently drop telemetry when Grafana is not configured (e.g. local dev without creds).
  if (!GRAFANA_ENDPOINT) {
    return new NextResponse(null, { status: 204 });
  }

  const body = await request.arrayBuffer();

  const headers: Record<string, string> = {
    "Content-Type": request.headers.get("Content-Type") ?? "application/x-protobuf",
  };
  if (GRAFANA_AUTH) {
    headers["Authorization"] = `Basic ${GRAFANA_AUTH}`;
  }

  const upstream = await fetch(`${GRAFANA_ENDPOINT}/v1/${signal}`, {
    method: "POST",
    headers,
    body,
  });

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {
      "Content-Type": upstream.headers.get("Content-Type") ?? "application/json",
    },
  });
}
