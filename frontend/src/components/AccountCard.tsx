// src/components/AccountCard.tsx

import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { formatMoney } from "@/lib/format";
import { ArrowDownLeft, ArrowUpRight, CalendarClock, Send } from "lucide-react";
import type { AccountResponse } from "@/api/accounts";

interface Props {
  account: AccountResponse;
  onDeposit?: () => void;
  onWithdraw?: () => void;
  onSchedule?: () => void;
  onSendETransfer?: () => void;
}

const ACCENT: Record<string, string> = {
  CHEQUING: "border-t-primary",
  SAVINGS:  "border-t-emerald-500",
  TFSA:     "border-t-violet-500",
};

export function AccountCard({ account, onDeposit, onWithdraw, onSchedule, onSendETransfer }: Props) {
  return (
    <Card className={`border-t-4 ${ACCENT[account.accountType] ?? "border-t-border"}`}>
      <CardContent className="pt-5 space-y-4">
        <div className="flex items-start justify-between">
          <div>
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
              {accountTypeLabel(account.accountType)}
            </p>
            <p className="text-xs text-muted-foreground font-mono mt-0.5">
              •••• {account.accountNumber.slice(-4)}
            </p>
          </div>
          {account.status !== "ACTIVE" && (
            <span className="text-xs px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-600 border border-amber-500/20">
              {account.status}
            </span>
          )}
        </div>

        <div>
          <p className="text-3xl font-bold tracking-tight">
            {formatMoney(account.balance, account.currency)}
          </p>
          <p className="text-xs text-muted-foreground mt-1">{account.currency} · Available balance</p>
        </div>

        <div className="grid grid-cols-4 gap-2">
          <ActionButton icon={ArrowDownLeft} label="Deposit" onClick={onDeposit} />
          <ActionButton icon={ArrowUpRight} label="Withdraw" onClick={onWithdraw} />
          <ActionButton icon={CalendarClock} label="Schedule" onClick={onSchedule} />
          <ActionButton icon={Send} label="e-Transfer" onClick={onSendETransfer} />
        </div>
      </CardContent>
    </Card>
  );
}

function ActionButton({ icon: Icon, label, onClick }: { icon: React.ElementType; label: string; onClick?: () => void }) {
  return (
    <button
      onClick={onClick}
      className="flex flex-col items-center gap-1.5 p-2 rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
    >
      <div className="w-8 h-8 rounded-full bg-muted flex items-center justify-center">
        <Icon className="w-4 h-4" />
      </div>
      <span className="text-[10px] font-medium leading-none">{label}</span>
    </button>
  );
}

function accountTypeLabel(t: string): string {
  switch (t) {
    case "CHEQUING": return "Chequing";
    case "SAVINGS":  return "Savings";
    case "TFSA":     return "TFSA";
    default:         return t;
  }
}
