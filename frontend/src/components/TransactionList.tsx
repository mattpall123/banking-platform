// src/components/TransactionList.tsx

import { useQuery } from "@tanstack/react-query";
import { transactionsApi, type TransactionResponse } from "@/api/transactions";
import { formatMoney, formatRelativeTime } from "@/lib/format";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";

interface Props {
  accountId: number;
  /** How many entries to show. Set null/undefined for all. */
  limit?: number;
}

export function TransactionList({ accountId, limit = 25 }: Props) {
  const query = useQuery({
    queryKey: ["transactions", accountId],
    queryFn: () => transactionsApi.forAccount(accountId),
  });

  if (query.isLoading) {
    return (
      <div className="space-y-2">
        {[...Array(5)].map((_, i) => (
          <div key={i} className="h-12 rounded bg-muted/30 animate-pulse" />
        ))}
      </div>
    );
  }

  if (query.isError) {
    return (
      <p className="text-sm text-destructive">
        Couldn't load transactions.
      </p>
    );
  }

  if (!query.data || query.data.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No transactions yet for this account.
      </p>
    );
  }

  const rows = limit ? query.data.slice(0, limit) : query.data;

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>When</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Description</TableHead>
          <TableHead className="text-right">Amount</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((tx, idx) => (
          <TableRow key={`${tx.journalEntryId}-${idx}`}>
            <TableCell className="text-muted-foreground text-sm">
              {formatRelativeTime(tx.occurredAt)}
            </TableCell>
            <TableCell>
              <TypeBadge type={tx.entryType} />
            </TableCell>
            <TableCell className="text-sm">{tx.description}</TableCell>
            <TableCell className={`text-right font-mono ${tx.signedAmount >= 0 ? "text-emerald-600" : "text-rose-600"}`}>
              {tx.signedAmount >= 0 ? "+" : ""}{formatMoney(tx.signedAmount, tx.currency)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function TypeBadge({ type }: { type: TransactionResponse["entryType"] }) {
  const styles: Record<TransactionResponse["entryType"], string> = {
    DEPOSIT:    "bg-emerald-500/10 text-emerald-600 border-emerald-500/20",
    WITHDRAWAL: "bg-rose-500/10 text-rose-600 border-rose-500/20",
    TRANSFER:   "bg-sky-500/10 text-sky-600 border-sky-500/20",
    ETRANSFER:  "bg-cyan-500/10 text-cyan-600 border-cyan-500/20",
    INTEREST:   "bg-violet-500/10 text-violet-600 border-violet-500/20",
    FEE:        "bg-amber-500/10 text-amber-600 border-amber-500/20",
    REVERSAL:   "bg-slate-500/10 text-slate-600 border-slate-500/20",
    ADJUSTMENT: "bg-slate-500/10 text-slate-600 border-slate-500/20",
    OPENING:    "bg-slate-500/10 text-slate-600 border-slate-500/20",
  };

  const label = type === "ETRANSFER"
    ? "e-Transfer"
    : type.charAt(0) + type.slice(1).toLowerCase();

  return (
    <span className={`text-xs px-2 py-0.5 rounded border ${styles[type]}`}>
      {label}
    </span>
  );
}