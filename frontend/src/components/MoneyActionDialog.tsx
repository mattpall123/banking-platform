// src/components/MoneyActionDialog.tsx
//
// Modal for depositing OR withdrawing. The `kind` prop switches behavior.

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { accountsApi, type AccountResponse } from "@/api/accounts";
import { ApiError } from "@/api/client";
import { formatMoney } from "@/lib/format";

interface Props {
  account: AccountResponse;
  kind: "deposit" | "withdraw";
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function MoneyActionDialog({ account, kind, open, onOpenChange }: Props) {
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState("");

  const mutation = useMutation({
    mutationFn: async () => {
      // Generate a fresh idempotency key per attempt. If the user clicks
      // submit twice rapidly, both attempts will use different keys —
      // that's fine because we disable the button while submitting.
      // For network-level retries, ApiError handling lets the user retry,
      // but we'd want to reuse the key for true at-most-once semantics
      // (a refinement for a later session).
      const key = `${kind}-${account.id}-${crypto.randomUUID()}`;
      const fn = kind === "deposit" ? accountsApi.deposit : accountsApi.withdraw;
      return fn(account.id, amount, account.currency, key);
    },
    onSuccess: () => {
      toast.success(
        `${kind === "deposit" ? "Deposited" : "Withdrew"} ${formatMoney(amount, account.currency)}`
      );
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      setAmount("");
      onOpenChange(false);
    },
    onError: (err) => {
      const msg =
        err instanceof ApiError
          ? (err.body as { message?: string } | null)?.message ?? `${err.status} error`
          : "Something went wrong";
      toast.error(msg);
    },
  });

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!amount || parseFloat(amount) <= 0) {
      toast.error("Enter a positive amount");
      return;
    }
    mutation.mutate();
  }

  const title = kind === "deposit" ? "Deposit cash" : "Withdraw cash";
  const desc = kind === "deposit"
    ? `Add money to your ${account.accountType.toLowerCase()} account.`
    : `Take money out of your ${account.accountType.toLowerCase()} account. Current balance: ${formatMoney(account.balance, account.currency)}.`;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{desc}</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="amount">Amount ({account.currency})</Label>
            <Input
              id="amount"
              type="number"
              step="0.01"
              min="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              required
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "Processing…" : (kind === "deposit" ? "Deposit" : "Withdraw")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}