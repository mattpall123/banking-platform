// src/components/StatementList.tsx

import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { statementsApi } from "@/api/statements";
import { ApiError } from "@/api/client";
import { Button } from "@/components/ui/button";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { formatMoney } from "@/lib/format";

const MONTH_NAMES = [
  "Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"
];

export function StatementList() {
  const query = useQuery({
    queryKey: ["statements"],
    queryFn: () => statementsApi.listMine(),
  });

  async function onDownload(id: number) {
    try {
      await statementsApi.download(id);
      toast.success("Statement downloaded");
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? (err.body as { message?: string } | null)?.message ?? `${err.status} error`
          : "Download failed";
      toast.error(msg);
    }
  }

  if (query.isLoading) {
    return (
      <div className="space-y-2">
        {[...Array(3)].map((_, i) => (
          <div key={i} className="h-12 rounded bg-muted/30 animate-pulse" />
        ))}
      </div>
    );
  }

  if (query.isError) {
    return (
      <p className="text-sm text-destructive">
        Couldn't load statements.
      </p>
    );
  }

  if (!query.data || query.data.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No statements available yet. Statements are generated monthly.
      </p>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Period</TableHead>
          <TableHead>Account</TableHead>
          <TableHead className="text-right">Transactions</TableHead>
          <TableHead className="text-right">Closing balance</TableHead>
          <TableHead className="text-right">Size</TableHead>
          <TableHead className="text-right">Action</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {query.data.map((s) => (
          <TableRow key={s.id}>
            <TableCell className="text-sm">
              {MONTH_NAMES[s.periodMonth - 1]} {s.periodYear}
            </TableCell>
            <TableCell className="text-sm font-mono">
              •••• {String(s.accountId).slice(-4)}
            </TableCell>
            <TableCell className="text-right text-sm">{s.transactionCount}</TableCell>
            <TableCell className="text-right text-sm font-mono">
              {formatMoney(s.closingBalance, s.currency)}
            </TableCell>
            <TableCell className="text-right text-sm text-muted-foreground">
              {(s.fileSizeBytes / 1024).toFixed(1)} KB
            </TableCell>
            <TableCell className="text-right">
              <Button size="sm" variant="outline" onClick={() => onDownload(s.id)}>
                Download
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}