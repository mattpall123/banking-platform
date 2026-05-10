// src/components/SendETransferDialog.tsx

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
import { etransfersApi, type SendETransferRequest } from "@/api/etransfers";
import { ApiError } from "@/api/client";
import type { AccountResponse } from "@/api/accounts";

interface Props {
  account: AccountResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function SendETransferDialog({ account, open, onOpenChange }: Props) {
  const queryClient = useQueryClient();
  const [recipientEmail, setRecipientEmail] = useState("");
  const [recipientName, setRecipientName] = useState("");
  const [amount, setAmount] = useState("");
  const [message, setMessage] = useState("");
  const [securityQuestion, setSecurityQuestion] = useState("");
  const [securityAnswer, setSecurityAnswer] = useState("");

  const mutation = useMutation({
    mutationFn: (req: SendETransferRequest) =>
      etransfersApi.send(req, `etx-${Date.now()}-${Math.random()}`),
    onSuccess: (data) => {
      const verb = data.autoDeposited ? "auto-deposited" : "sent";
      toast.success(`e-Transfer ${verb}: $${data.amount} to ${data.recipientEmail}`);
      queryClient.invalidateQueries({ queryKey: ["etransfers"] });
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      reset();
      onOpenChange(false);
    },
    onError: (err) => {
      const msg =
        err instanceof ApiError
          ? (err.body as { message?: string } | null)?.message ?? `${err.status} error`
          : "Failed to send";
      toast.error(msg);
    },
  });

  function reset() {
    setRecipientEmail("");
    setRecipientName("");
    setAmount("");
    setMessage("");
    setSecurityQuestion("");
    setSecurityAnswer("");
  }

  function handleSubmit() {
    const req: SendETransferRequest = {
      sourceAccountId: account.id,
      recipientEmail: recipientEmail.trim(),
      recipientName: recipientName.trim(),
      amount: parseFloat(amount),
      currency: account.currency,
      message: message.trim() || null,
      securityQuestion: securityQuestion.trim() || null,
      securityAnswer: securityAnswer.trim() || null,
    };
    mutation.mutate(req);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Send Interac e-Transfer</DialogTitle>
          <DialogDescription>
            From {account.accountType} •••• {account.accountNumber.slice(-4)}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 py-2">
          <div>
            <Label htmlFor="recipient-email">Recipient email</Label>
            <Input
              id="recipient-email"
              type="email"
              value={recipientEmail}
              onChange={(e) => setRecipientEmail(e.target.value)}
              placeholder="someone@example.com"
            />
            <p className="text-xs text-muted-foreground mt-1">
              If they have auto-deposit enabled, no security question needed.
            </p>
          </div>

          <div>
            <Label htmlFor="recipient-name">Recipient name</Label>
            <Input
              id="recipient-name"
              value={recipientName}
              onChange={(e) => setRecipientName(e.target.value)}
              placeholder="Their full name"
            />
          </div>

          <div>
            <Label htmlFor="etx-amount">Amount ({account.currency})</Label>
            <Input
              id="etx-amount"
              type="number"
              step="0.01"
              min="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="50.00"
            />
          </div>

          <div>
            <Label htmlFor="etx-message">Message (optional)</Label>
            <Input
              id="etx-message"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="What's this for?"
              maxLength={400}
            />
          </div>

          <div className="border-t pt-3 mt-3">
            <p className="text-xs text-muted-foreground mb-2">
              Security question (skipped automatically if recipient has auto-deposit)
            </p>
            <div className="space-y-2">
              <div>
                <Label htmlFor="etx-q">Question</Label>
                <Input
                  id="etx-q"
                  value={securityQuestion}
                  onChange={(e) => setSecurityQuestion(e.target.value)}
                  placeholder="What city were we in?"
                  maxLength={140}
                />
              </div>
              <div>
                <Label htmlFor="etx-a">Answer</Label>
                <Input
                  id="etx-a"
                  value={securityAnswer}
                  onChange={(e) => setSecurityAnswer(e.target.value)}
                  placeholder="Toronto"
                  maxLength={100}
                />
              </div>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={mutation.isPending}>
            {mutation.isPending ? "Sending..." : "Send e-Transfer"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}