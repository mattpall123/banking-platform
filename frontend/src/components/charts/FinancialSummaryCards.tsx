// src/components/charts/FinancialSummaryCards.tsx

import { useMemo } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Wallet, TrendingUp, TrendingDown, Zap } from "lucide-react";
import { formatMoney } from "@/lib/format";
import type { AccountResponse } from "@/api/accounts";
import type { TransactionResponse } from "@/api/transactions";

interface Props {
  accounts: AccountResponse[];
  allTransactions: TransactionResponse[];
}

export function FinancialSummaryCards({ accounts, allTransactions }: Props) {
  const { totalBalance, monthlyChange, biggestTransaction } = useMemo(() => {
    const total = accounts.reduce((s, a) => s + a.balance, 0);

    const now = new Date();
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).toISOString();
    const thisMonth = allTransactions
      .filter((t) => t.occurredAt >= monthStart)
      .reduce((s, t) => s + t.signedAmount, 0);

    const biggest = allTransactions.length > 0
      ? Math.max(...allTransactions.map((t) => Math.abs(t.signedAmount)))
      : 0;

    return { totalBalance: total, monthlyChange: thisMonth, biggestTransaction: biggest };
  }, [accounts, allTransactions]);

  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-muted-foreground mb-1">Total balance</p>
              <p className="text-2xl font-bold">{formatMoney(totalBalance, "CAD")}</p>
              <p className="text-xs text-muted-foreground mt-1">across {accounts.length} account{accounts.length !== 1 ? "s" : ""}</p>
            </div>
            <div className="w-9 h-9 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
              <Wallet className="w-5 h-5 text-primary" />
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-muted-foreground mb-1">This month</p>
              <p className={`text-2xl font-bold ${monthlyChange >= 0 ? "text-emerald-600 dark:text-emerald-400" : "text-destructive"}`}>
                {monthlyChange >= 0 ? "+" : ""}{formatMoney(monthlyChange, "CAD")}
              </p>
              <p className="text-xs text-muted-foreground mt-1">net change</p>
            </div>
            <div className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${monthlyChange >= 0 ? "bg-emerald-500/10" : "bg-destructive/10"}`}>
              {monthlyChange >= 0
                ? <TrendingUp className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />
                : <TrendingDown className="w-5 h-5 text-destructive" />}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-muted-foreground mb-1">Largest transaction</p>
              <p className="text-2xl font-bold">{biggestTransaction > 0 ? formatMoney(biggestTransaction, "CAD") : "—"}</p>
              <p className="text-xs text-muted-foreground mt-1">all time</p>
            </div>
            <div className="w-9 h-9 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
              <Zap className="w-5 h-5 text-primary" />
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
