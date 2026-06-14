const API_ERROR_PREFIX = /^API error \d+:\s*/;

export function extractApiErrorMessage(err: unknown): string {
  if (err instanceof Error) {
    return err.message.replace(API_ERROR_PREFIX, "");
  }
  return "An unexpected error occurred";
}

export function isRuleViolationError(err: unknown): boolean {
  return err instanceof Error && (err as Error & { status?: number }).status === 422;
}
