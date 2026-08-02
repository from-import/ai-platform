const numberFormatter = new Intl.NumberFormat();

export function formatNumber(value: number): string {
  return numberFormatter.format(value);
}

export function formatProvider(provider: string): string {
  if (!provider) {
    return "-";
  }
  return provider.charAt(0).toUpperCase() + provider.slice(1);
}

export function formatRequestedAt(requestedAt: string | null): string {
  return requestedAt ? requestedAt.replace("T", " ").slice(0, 19) : "-";
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "An unexpected error occurred";
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}
