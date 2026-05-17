// src/components/charts/SpendingOverviewChart.tsx

import { useMemo } from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatMoney } from "@/lib/format";
import type { TransactionResponse } from "@/api/transactions";

interface Props {
  transactions: TransactionResponse[];
}

const COLORS = [
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
];

export function SpendingOverviewChart({ transactions }: Props) {
  const data = useMemo(() => {
    const moneyIn = transactions.filter((t) => t.signedAmount > 0 && !["TRANSFER", "SCHEDULED_TRANSFER"].includes(t.entryType));
    const moneyOut = transactions.filter((t) => t.signedAmount < 0 && !["TRANSFER", "SCHEDULED_TRANSFER"].includes(t.entryType));
    const transfers = transactions.filter((t) => t.entryType === "TRANSFER" || t.entryType === "ETRANSFER");
    const scheduled = transactions.filter((t) => t.entryType === "SCHEDULED_TRANSFER" as string);

    const sum = (arr: TransactionResponse[]) =>
      arr.reduce((s, t) => s + Math.abs(t.signedAmount), 0);

    return [
      { name: "Money In", amount: sum(moneyIn) },
      { name: "Money Out", amount: sum(moneyOut) },
      { name: "Transfers", amount: sum(transfers) },
      { name: "Scheduled", amount: sum(scheduled) },
    ].filter((d) => d.amount > 0);
  }, [transactions]);

  if (data.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base font-medium">Spending overview</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-center h-48 text-sm text-muted-foreground">
          No transaction data yet
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base font-medium">Spending overview</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={data} margin={{ top: 4, right: 4, left: 4, bottom: 4 }}>
            <XAxis
              dataKey="name"
              tick={{ fontSize: 12 }}
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              tick={{ fontSize: 11 }}
              axisLine={false}
              tickLine={false}
              tickFormatter={(v) => `$${(v / 1000).toFixed(0)}k`}
              width={48}
            />
            <Tooltip
              formatter={(v) => [formatMoney(v as number, "CAD"), "Total"]}
              contentStyle={{
                borderRadius: "var(--radius)",
                border: "1px solid var(--border)",
                background: "var(--card)",
                color: "var(--card-foreground)",
                fontSize: 13,
              }}
              cursor={{ fill: "var(--muted)" }}
            />
            <Bar dataKey="amount" radius={[4, 4, 0, 0]}>
              {data.map((_entry, index) => (
                <Cell key={index} fill={COLORS[index % COLORS.length]} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
