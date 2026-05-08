// src/pages/DashboardPage.tsx

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { accountsApi, type AccountResponse } from "@/api/accounts";
import { useAuth } from "@/auth/useAuth";
import { Button } from "@/components/ui/button";
import { AccountCard } from "@/components/AccountCard";
import { MoneyActionDialog } from "@/components/MoneyActionDialog";
import { TransferForm } from "@/components/TransferForm";
import { TransactionList } from "@/components/TransactionList";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EnvBanner } from "@/components/EnvBanner";
import { StatementList } from "@/components/StatementList";
import { ScheduledTransferDialog } from "@/components/ScheduledTransferDialog";
import { ScheduledTransferList } from "@/components/ScheduledTransferList";

interface DialogState {
  account: AccountResponse;
  kind: "deposit" | "withdraw";
}

export function DashboardPage() {
  const { user, logout } = useAuth();
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [scheduleDialogAccount, setScheduleDialogAccount] = useState<AccountResponse | null>(null);
  const [historyAccountIdRaw, setHistoryAccountId] = useState<number | null>(null);

  const accountsQuery = useQuery({
    queryKey: ["accounts"],
    queryFn: () => accountsApi.listMine(),
  });

  // Derive the active history account: user's choice, else first loaded.
  const historyAccountId =
    historyAccountIdRaw ?? accountsQuery.data?.[0]?.id ?? null;

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b">
        <div className="max-w-5xl mx-auto px-6 py-4 flex justify-between items-center">
          <h1 className="text-xl font-semibold">Banking Platform</h1>
          <div className="flex items-center gap-4">
            <span className="text-sm text-muted-foreground">{user?.email}</span>
            <Button variant="outline" size="sm" onClick={logout}>Log out</Button>
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-8 space-y-8">
        {/* Accounts */}
        <section>
          <h2 className="text-lg font-medium mb-4">Your accounts</h2>

          {accountsQuery.isLoading && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="h-40 rounded-lg border bg-muted/30 animate-pulse" />
              <div className="h-40 rounded-lg border bg-muted/30 animate-pulse" />
            </div>
          )}

          {accountsQuery.isError && (
            <p className="text-sm text-destructive">
              Couldn't load your accounts. Please refresh the page.
            </p>
          )}

          {accountsQuery.data && accountsQuery.data.length === 0 && (
            <p className="text-sm text-muted-foreground">
              You don't have any accounts yet.
            </p>
          )}

          {accountsQuery.data && accountsQuery.data.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {accountsQuery.data.map((acc) => (
                <AccountCard
                  key={acc.id}
                  account={acc}
                  onDeposit={() => setDialog({ account: acc, kind: "deposit" })}
                  onWithdraw={() => setDialog({ account: acc, kind: "withdraw" })}
                  onSchedule={() => setScheduleDialogAccount(acc)}
                />
              ))}
            </div>
          )}
        </section>

        {/* Transfer */}
        {accountsQuery.data && accountsQuery.data.length > 0 && (
          <section>
            <h2 className="text-lg font-medium mb-4">Transfer</h2>
            <TransferForm accounts={accountsQuery.data} />
          </section>
        )}

        {/* Transaction history */}
        {accountsQuery.data && accountsQuery.data.length > 0 && historyAccountId !== null && (
          <section>
            <h2 className="text-lg font-medium mb-4">Recent transactions</h2>
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="text-base font-medium">
                    Transaction history
                  </CardTitle>
                  <select
                    value={historyAccountId}
                    onChange={(e) => setHistoryAccountId(Number(e.target.value))}
                    className="flex h-9 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-ring"
                  >
                    {accountsQuery.data.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.accountType} •••• {a.accountNumber.slice(-4)}
                      </option>
                    ))}
                  </select>
                </div>
              </CardHeader>
              <CardContent>
                <TransactionList accountId={historyAccountId} />
              </CardContent>
            </Card>
          </section>
        )}

        {/* Statements */}
        {accountsQuery.data && accountsQuery.data.length > 0 && (
          <section>
            <h2 className="text-lg font-medium mb-4">Statements</h2>
            <Card>
              <CardHeader>
                <CardTitle className="text-base font-medium">
                  Monthly statements
                </CardTitle>
              </CardHeader>
              <CardContent>
                <StatementList />
              </CardContent>
            </Card>
          </section>
        )}

        {/* Scheduled transfers */}
        {accountsQuery.data && accountsQuery.data.length > 0 && (
          <section>
            <h2 className="text-lg font-medium mb-4">Scheduled transfers</h2>
            <Card>
              <CardHeader>
                <CardTitle className="text-base font-medium">
                  Recurring & one-off scheduled transfers
                </CardTitle>
              </CardHeader>
              <CardContent>
                <ScheduledTransferList />
              </CardContent>
            </Card>
          </section>
        )}
      </main>

      {dialog && (
        <MoneyActionDialog
          account={dialog.account}
          kind={dialog.kind}
          open={true}
          onOpenChange={(open) => { if (!open) setDialog(null); }}
        />
      )}

      {scheduleDialogAccount && (
        <ScheduledTransferDialog
          account={scheduleDialogAccount}
          open={true}
          onOpenChange={(open) => { if (!open) setScheduleDialogAccount(null); }}
        />
      )}

      <EnvBanner />
    </div>
  );
}