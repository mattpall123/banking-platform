// src/components/ETransferList.tsx

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { etransfersApi, type ETransferResponse, type ETransferStatus, type IncomingETransferResponse } from "@/api/etransfers";
import { ApiError } from "@/api/client";
import { formatMoney } from "@/lib/format";
import type { AccountResponse } from "@/api/accounts";
import { ClaimETransferDialog } from "@/components/ClaimETransferDialog";

const STATUS_STYLES: Record<ETransferStatus, string> = {
  PENDING:   "text-amber-700",
  COMPLETED: "text-emerald-700",
  CANCELLED: "text-muted-foreground",
  EXPIRED:   "text-muted-foreground",
  LOCKED:    "text-rose-700",
};

interface Props {
  accounts: AccountResponse[];
}

export function ETransferList({ accounts }: Props) {
  const queryClient = useQueryClient();
  const [claimTarget, setClaimTarget] = useState<IncomingETransferResponse | null>(null);

  const incomingQuery = useQuery({
    queryKey: ["etransfers", "incoming"],
    queryFn: () => etransfersApi.incoming(),
  });

  const outgoingQuery = useQuery({
    queryKey: ["etransfers", "outgoing"],
    queryFn: () => etransfersApi.outgoing(),
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => etransfersApi.cancel(id),
    onSuccess: () => {
      toast.success("Cancelled");
      queryClient.invalidateQueries({ queryKey: ["etransfers"] });
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
    },
    onError: (err) => {
      const msg =
        err instanceof ApiError
          ? (err.body as { message?: string } | null)?.message ?? "Failed"
          : "Failed";
      toast.error(msg);
    },
  });

  const incoming = incomingQuery.data ?? [];
  const outgoing = outgoingQuery.data ?? [];

  return (
    <div className="space-y-6">
      {/* INCOMING */}
      <div>
        <h3 className="text-sm font-medium text-muted-foreground mb-2">Incoming</h3>
        {incomingQuery.isLoading ? (
          <div className="h-12 rounded bg-muted/30 animate-pulse" />
        ) : incoming.length === 0 ? (
          <p className="text-sm text-muted-foreground">No pending incoming transfers.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>From</TableHead>
                <TableHead>Question</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead>Expires</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {incoming.map((e) => (
                <TableRow key={e.id}>
                  <TableCell className="text-sm">{e.recipientName}</TableCell>
                  <TableCell className="text-sm">{e.securityQuestion}</TableCell>
                  <TableCell className="text-right text-sm font-mono">
                    {formatMoney(e.amount, e.currency)}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {new Date(e.expiresAt).toLocaleString(undefined, {
                      month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
                    })}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" onClick={() => setClaimTarget(e)}>
                      Claim
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      {/* OUTGOING */}
      <div>
        <h3 className="text-sm font-medium text-muted-foreground mb-2">Outgoing (recent)</h3>
        {outgoingQuery.isLoading ? (
          <div className="h-12 rounded bg-muted/30 animate-pulse" />
        ) : outgoing.length === 0 ? (
          <p className="text-sm text-muted-foreground">No outgoing transfers yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>To</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Sent</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {outgoing.slice(0, 10).map((e: ETransferResponse) => (
                <TableRow key={e.id}>
                  <TableCell className="text-sm">
                    {e.recipientEmail}
                    {e.autoDeposited && (
                      <span className="ml-2 text-xs px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
                        auto
                      </span>
                    )}
                  </TableCell>
                  <TableCell className="text-right text-sm font-mono">
                    {formatMoney(e.amount, e.currency)}
                  </TableCell>
                  <TableCell>
                    <span className={`text-sm ${STATUS_STYLES[e.status]}`}>
                      {e.status}
                    </span>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {new Date(e.createdAt).toLocaleString(undefined, {
                      month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
                    })}
                  </TableCell>
                  <TableCell className="text-right">
                    {e.status === "PENDING" && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => cancelMutation.mutate(e.id)}
                        disabled={cancelMutation.isPending}
                      >
                        Cancel
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      {claimTarget && (
        <ClaimETransferDialog
          transfer={claimTarget}
          accounts={accounts}
          open={true}
          onOpenChange={(open) => { if (!open) setClaimTarget(null); }}
        />
      )}
    </div>
  );
}