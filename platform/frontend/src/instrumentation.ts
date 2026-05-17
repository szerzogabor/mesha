export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    const { default: serverInit } = await import("./sentry.server.config");
    void serverInit;
  }

  if (process.env.NEXT_RUNTIME === "edge") {
    const { default: edgeInit } = await import("./sentry.edge.config");
    void edgeInit;
  }
}
