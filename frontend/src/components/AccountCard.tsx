// src/components/AccountCard.tsx

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { formatMoney } from "@/lib/format";
import type { AccountResponse } from "@/api/accounts";

interface Props {
  account: AccountResponse;
  onDeposit?: () => void;
  onWithdraw?: () => void;
}

export function AccountCard({ account, onDeposit, onWithdraw }: Props) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between">
          <div>
            <CardTitle className="text-base font-medium text-muted-foreground">
              {accountTypeLabel(account.accountType)}
            </CardTitle>
            <p className="text-xs text-muted-foreground mt-1 font-mono">
              •••• {account.accountNumber.slice(-4)}
            </p>
          </div>
          {account.status !== "ACTIVE" && (
            <span className="text-xs px-2 py-0.5 rounded bg-amber-500/10 text-amber-600 border border-amber-500/20">
              {account.status}
            </span>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-3xl font-semibold tracking-tight">
          {formatMoney(account.balance, account.currency)}
        </p>
        <div className="flex gap-2">
          <Button size="sm" variant="outline" onClick={onDeposit}>Deposit</Button>
          <Button size="sm" variant="outline" onClick={onWithdraw}>Withdraw</Button>
        </div>
      </CardContent>
    </Card>
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