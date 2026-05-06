// src/lib/format.ts
//
// Money formatting. Always go through here, never call toFixed() inline.
// Uses Intl.NumberFormat which handles currency symbols, thousands
// separators, and locale conventions correctly.

export function formatMoney(amount: number | string, currency = "CAD"): string {
  const n = typeof amount === "string" ? parseFloat(amount) : amount;
  return new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency,
  }).format(n);
}

/** Short relative time, e.g. "5 min ago", "2h ago", "Mar 14". */
export function formatRelativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  const now = Date.now();
  const diffMs = now - then;
  const diffMin = Math.floor(diffMs / 60_000);

  if (diffMin < 1)   return "just now";
  if (diffMin < 60)  return `${diffMin} min ago`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24)    return `${diffH}h ago`;
  const diffD = Math.floor(diffH / 24);
  if (diffD < 7)     return `${diffD}d ago`;

  return new Date(iso).toLocaleDateString("en-CA", { month: "short", day: "numeric" });
}