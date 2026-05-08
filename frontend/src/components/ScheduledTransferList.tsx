// src/components/ScheduledTransferList.tsx

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import {
  scheduledTransfersApi,
  type ScheduledTransferResponse,
} from "@/api/scheduledTransfers";
import { ApiError } from "@/api/client";
import { formatMoney } from "@/lib/format";

const DAY_LABELS = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export function ScheduledTransferList() {
  const queryClient = useQueryClient();
  const [cancellingId, setCancellingId] = useState<number | null>(null);

  const query = useQuery({
    queryKey: ["scheduled-transfers"],
    queryFn: () => scheduledTransfersApi.listMine(),
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => scheduledTransfersApi.cancel(id),
    onSuccess: () => {
      toast.success("Scheduled transfer cancelled");
      queryClient.invalidateQueries({ queryKey: ["scheduled-transfers"] });
    },
    onError: (err) => {
      const msg =
        err instanceof ApiError
          ? (err.body as { message?: string } | null)?.message ?? `${err.status} error`
          : "Failed to cancel";
      toast.error(msg);
    },
    onSettled: () => setCancellingId(null),
  });

  function frequencyLabel(s: ScheduledTransferResponse): string {
    switch (s.frequency) {
      case "ONCE":   return "Once";
      case "DAILY":  return "Daily";
      case "WEEKLY": return s.dayOfWeek ? `Weekly (${DAY_LABELS[s.dayOfWeek]})` : "Weekly";
      case "MONTHLY": return s.dayOfMonth ? `Monthly (day ${s.dayOfMonth})` : "Monthly";
    }
  }

  if (query.isLoading) {
    return (
      <div className="space-y-2">
        {[...Array(2)].map((_, i) => (
          <div key={i} className="h-12 rounded bg-muted/30 animate-pulse" />
        ))}
      </div>
    );
  }

  if (query.isError) {
    return <p className="text-sm text-destructive">Couldn't load scheduled transfers.</p>;
  }

  if (!query.data || query.data.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No scheduled transfers yet. Click "Schedule transfer" on an account card to create one.
      </p>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>To</TableHead>
          <TableHead>Description</TableHead>
          <TableHead>Frequency</TableHead>
          <TableHead className="text-right">Amount</TableHead>
          <TableHead>Next run</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Action</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {query.data.map((s) => (
          <TableRow key={s.id}>
            <TableCell className="text-sm font-mono">
              •••• {s.destinationAccountNumber.slice(-4)}
            </TableCell>
            <TableCell className="text-sm">{s.description}</TableCell>
            <TableCell className="text-sm">{frequencyLabel(s)}</TableCell>
            <TableCell className="text-right text-sm font-mono">
              {formatMoney(s.amount, s.currency)}
            </TableCell>
            <TableCell className="text-sm">
              {new Date(s.nextRunAt).toLocaleString(undefined, {
                month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
              })}
            </TableCell>
            <TableCell>
              <span className={
                s.status === "ACTIVE" ? "text-emerald-700 text-sm" :
                s.status === "CANCELLED" ? "text-muted-foreground text-sm" :
                s.status === "EXPIRED" ? "text-muted-foreground text-sm" :
                "text-amber-700 text-sm"
              }>
                {s.status}
              </span>
            </TableCell>
            <TableCell className="text-right">
              {s.status === "ACTIVE" && (
                <Button
                  size="sm"
                  variant="outline"
                  disabled={cancellingId === s.id}
                  onClick={() => {
                    setCancellingId(s.id);
                    cancelMutation.mutate(s.id);
                  }}
                >
                  {cancellingId === s.id ? "Cancelling..." : "Cancel"}
                </Button>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}