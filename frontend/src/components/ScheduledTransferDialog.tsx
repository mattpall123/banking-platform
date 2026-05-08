// src/components/ScheduledTransferDialog.tsx

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
import {
  scheduledTransfersApi,
  type CreateScheduledTransferRequest,
  type Frequency,
} from "@/api/scheduledTransfers";
import { ApiError } from "@/api/client";
import type { AccountResponse } from "@/api/accounts";

interface Props {
  account: AccountResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const DAYS_OF_WEEK = [
  { value: 1, label: "Mon" },
  { value: 2, label: "Tue" },
  { value: 3, label: "Wed" },
  { value: 4, label: "Thu" },
  { value: 5, label: "Fri" },
  { value: 6, label: "Sat" },
  { value: 7, label: "Sun" },
];

export function ScheduledTransferDialog({ account, open, onOpenChange }: Props) {
  const queryClient = useQueryClient();
  const [destAccount, setDestAccount] = useState("");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [frequency, setFrequency] = useState<Frequency>("WEEKLY");
  const [dayOfWeek, setDayOfWeek] = useState<number>(5);   // Friday
  const [dayOfMonth, setDayOfMonth] = useState<number>(1);
  const [startDate, setStartDate] = useState(
    new Date().toISOString().slice(0, 10)
  );

  const mutation = useMutation({
    mutationFn: (req: CreateScheduledTransferRequest) => scheduledTransfersApi.create(req),
    onSuccess: () => {
      toast.success("Scheduled transfer created");
      queryClient.invalidateQueries({ queryKey: ["scheduled-transfers"] });
      reset();
      onOpenChange(false);
    },
    onError: (err) => {
      const msg =
        err instanceof ApiError
          ? (err.body as { message?: string } | null)?.message ?? `${err.status} error`
          : "Failed to create";
      toast.error(msg);
    },
  });

  function reset() {
    setDestAccount("");
    setAmount("");
    setDescription("");
    setFrequency("WEEKLY");
    setDayOfWeek(5);
    setDayOfMonth(1);
    setStartDate(new Date().toISOString().slice(0, 10));
  }

  function handleSubmit() {
    const req: CreateScheduledTransferRequest = {
      sourceAccountId: account.id,
      destinationAccountNumber: destAccount,
      amount: parseFloat(amount),
      currency: account.currency,
      description,
      frequency,
      startDate,
      // Frequency-specific fields
      dayOfWeek: frequency === "WEEKLY" ? dayOfWeek : null,
      dayOfMonth: frequency === "MONTHLY" ? dayOfMonth : null,
    };
    mutation.mutate(req);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Schedule a transfer</DialogTitle>
          <DialogDescription>
            From {account.accountType} •••• {account.accountNumber.slice(-4)}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 py-2">
          <div>
            <Label htmlFor="dest">Destination account number</Label>
            <Input
              id="dest"
              value={destAccount}
              onChange={(e) => setDestAccount(e.target.value)}
              placeholder="100000000002"
              maxLength={12}
            />
          </div>

          <div>
            <Label htmlFor="amount">Amount ({account.currency})</Label>
            <Input
              id="amount"
              type="number"
              step="0.01"
              min="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="50.00"
            />
          </div>

          <div>
            <Label htmlFor="description">Description</Label>
            <Input
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Weekly transfer to savings"
              maxLength={140}
            />
          </div>

          <div>
            <Label htmlFor="frequency">Frequency</Label>
            <select
              id="frequency"
              value={frequency}
              onChange={(e) => setFrequency(e.target.value as Frequency)}
              className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-ring"
            >
              <option value="ONCE">Once</option>
              <option value="DAILY">Daily</option>
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
            </select>
          </div>

          {frequency === "WEEKLY" && (
            <div>
              <Label>Day of week</Label>
              <div className="flex gap-1 mt-1">
                {DAYS_OF_WEEK.map((d) => (
                  <Button
                    key={d.value}
                    type="button"
                    variant={dayOfWeek === d.value ? "default" : "outline"}
                    size="sm"
                    onClick={() => setDayOfWeek(d.value)}
                    className="flex-1"
                  >
                    {d.label}
                  </Button>
                ))}
              </div>
            </div>
          )}

          {frequency === "MONTHLY" && (
            <div>
              <Label htmlFor="dom">Day of month (1-28)</Label>
              <Input
                id="dom"
                type="number"
                min="1"
                max="28"
                value={dayOfMonth}
                onChange={(e) => setDayOfMonth(Number(e.target.value))}
              />
            </div>
          )}

          <div>
            <Label htmlFor="startDate">Start date</Label>
            <Input
              id="startDate"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={mutation.isPending}>
            {mutation.isPending ? "Creating..." : "Schedule transfer"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}