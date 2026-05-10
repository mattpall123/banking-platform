// src/components/ClaimETransferDialog.tsx

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Dialog, DialogContent, DialogDescription,
  DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { etransfersApi, type IncomingETransferResponse } from "@/api/etransfers";
import { ApiError } from "@/api/client";
import type { AccountResponse } from "@/api/accounts";
import { formatMoney } from "@/lib/format";

interface Props {
  transfer: IncomingETransferResponse;
  accounts: AccountResponse[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ClaimETransferDialog({ transfer, accounts, open, onOpenChange }: Props) {
  const queryClient = useQueryClient();
  const matchingAccounts = accounts.filter((a) => a.currency === transfer.currency && a.status === "ACTIVE");
  const [depositAccountId, setDepositAccountId] = useState<number>(matchingAccounts[0]?.id ?? 0);
  const [answer, setAnswer] = useState("");

  const mutation = useMutation({
    mutationFn: () =>
      etransfersApi.claim(transfer.id, {
        depositAccountId,
        securityAnswer: answer,
      }),
    onSuccess: () => {
      toast.success(`Claimed ${formatMoney(transfer.amount, transfer.currency)}`);
      queryClient.invalidateQueries({ queryKey: ["etransfers"] });
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      setAnswer("");
      onOpenChange(false);
    },
    onError: (err) => {
      const body = err instanceof ApiError ? (err.body as { message?: string; attemptsRemaining?: number }) : null;
      if (body?.attemptsRemaining !== undefined) {
        toast.error(`${body.message ?? "Wrong answer"} (${body.attemptsRemaining} left)`);
        if (body.attemptsRemaining === 0) {
          // locked out, refresh the list
          queryClient.invalidateQueries({ queryKey: ["etransfers"] });
          onOpenChange(false);
        }
      } else {
        toast.error(body?.message ?? "Claim failed");
      }
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Claim e-Transfer</DialogTitle>
          <DialogDescription>
            {formatMoney(transfer.amount, transfer.currency)} from {transfer.recipientName || transfer.recipientEmail}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 py-2">
          {transfer.message && (
            <div className="text-sm bg-muted/30 rounded p-2 italic">
              "{transfer.message}"
            </div>
          )}

          <div>
            <Label>Security question</Label>
            <p className="text-sm font-medium mt-1">{transfer.securityQuestion}</p>
          </div>

          <div>
            <Label htmlFor="claim-answer">Your answer</Label>
            <Input
              id="claim-answer"
              value={answer}
              onChange={(e) => setAnswer(e.target.value)}
              autoFocus
            />
            <p className="text-xs text-muted-foreground mt-1">
              {transfer.attemptsRemaining} attempt{transfer.attemptsRemaining === 1 ? "" : "s"} remaining
            </p>
          </div>

          <div>
            <Label htmlFor="deposit-account">Deposit into</Label>
            <select
              id="deposit-account"
              value={depositAccountId}
              onChange={(e) => setDepositAccountId(Number(e.target.value))}
              className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-ring"
            >
              {matchingAccounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.accountType} •••• {a.accountNumber.slice(-4)} ({formatMoney(a.balance, a.currency)})
                </option>
              ))}
            </select>
            {matchingAccounts.length === 0 && (
              <p className="text-xs text-destructive mt-1">
                No active {transfer.currency} accounts to deposit into.
              </p>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !answer || matchingAccounts.length === 0}
          >
            {mutation.isPending ? "Claiming..." : "Claim"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}