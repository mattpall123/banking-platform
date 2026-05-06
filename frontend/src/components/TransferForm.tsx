// src/components/TransferForm.tsx

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { transfersApi } from "@/api/transfers";
import { ApiError } from "@/api/client";
import type { AccountResponse } from "@/api/accounts";
import { formatMoney } from "@/lib/format";

interface Props {
  accounts: AccountResponse[];
}

export function TransferForm({ accounts }: Props) {
  const queryClient = useQueryClient();
  const [sourceAccountId, setSourceAccountId] = useState<number | "">(
    accounts[0]?.id ?? ""
  );
  const [destinationAccountNumber, setDestinationAccountNumber] = useState("");
  const [amount, setAmount] = useState("");

  const mutation = useMutation({
    mutationFn: () => {
      if (sourceAccountId === "") throw new Error("Pick a source account");
      const source = accounts.find((a) => a.id === sourceAccountId)!;
      const key = `transfer-${sourceAccountId}-${crypto.randomUUID()}`;
      return transfersApi.transfer(
        sourceAccountId,
        destinationAccountNumber,
        amount,
        source.currency,
        key
      );
    },
    onSuccess: () => {
      toast.success(`Transferred ${formatMoney(amount)} successfully`);
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      setDestinationAccountNumber("");
      setAmount("");
    },
    onError: (err) => {
      const msg =
        err instanceof ApiError
          ? (err.body as { message?: string } | null)?.message ?? `${err.status} error`
          : err.message;
      toast.error(msg);
    },
  });

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!amount || parseFloat(amount) <= 0) {
      toast.error("Enter a positive amount");
      return;
    }
    if (!destinationAccountNumber) {
      toast.error("Enter a destination account number");
      return;
    }
    mutation.mutate();
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Transfer money</CardTitle>
        <CardDescription>
          Move funds between your accounts or send to any account number.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="source">From account</Label>
            <select
              id="source"
              value={sourceAccountId}
              onChange={(e) => setSourceAccountId(Number(e.target.value))}
              className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-ring"
              required
            >
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.accountType} •••• {a.accountNumber.slice(-4)} — {formatMoney(a.balance, a.currency)}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="dest">To account number</Label>
            <Input
              id="dest"
              value={destinationAccountNumber}
              onChange={(e) => setDestinationAccountNumber(e.target.value)}
              placeholder="100000000002"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="transferAmount">Amount</Label>
            <Input
              id="transferAmount"
              type="number"
              step="0.01"
              min="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              required
            />
          </div>

          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? "Sending…" : "Send transfer"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}