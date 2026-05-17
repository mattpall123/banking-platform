// src/components/charts/BalanceHistoryChart.tsx

import { useMemo } from "react";
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from "recharts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatMoney } from "@/lib/format";
import type { TransactionResponse } from "@/api/transactions";

interface Props {
  transactions: TransactionResponse[];
  currentBalance: number;
}

function formatShortDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-CA", { month: "short", day: "numeric" });
}

export function BalanceHistoryChart({ transactions, currentBalance }: Props) {
  const data = useMemo(() => {
    if (transactions.length === 0) return [];

    const sorted = [...transactions].sort(
      (a, b) => new Date(a.occurredAt).getTime() - new Date(b.occurredAt).getTime()
    );

    let running = currentBalance;
    const points: { date: string; balance: number }[] = [];

    for (let i = sorted.length - 1; i >= 0; i--) {
      points.unshift({ date: sorted[i].occurredAt, balance: running });
      running -= sorted[i].signedAmount;
    }

    // Add the opening point (before first transaction)
    points.unshift({ date: sorted[0].occurredAt, balance: running });

    // De-duplicate to at most 30 points for readability
    if (points.length > 30) {
      const step = Math.ceil(points.length / 30);
      return points.filter((_, i) => i % step === 0 || i === points.length - 1);
    }

    return points;
  }, [transactions, currentBalance]);

  if (data.length < 2) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base font-medium">Balance history</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-center h-48 text-sm text-muted-foreground">
          Not enough data yet
        </CardContent>
      </Card>
    );
  }

  const minBalance = Math.min(...data.map((d) => d.balance));
  const maxBalance = Math.max(...data.map((d) => d.balance));
  const padding = (maxBalance - minBalance) * 0.15 || 100;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base font-medium">Balance history</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={220}>
          <AreaChart data={data} margin={{ top: 4, right: 4, left: 4, bottom: 4 }}>
            <defs>
              <linearGradient id="balanceGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--chart-1)" stopOpacity={0.25} />
                <stop offset="95%" stopColor="var(--chart-1)" stopOpacity={0} />
              </linearGradient>
            </defs>
            <XAxis
              dataKey="date"
              tickFormatter={formatShortDate}
              tick={{ fontSize: 11 }}
              axisLine={false}
              tickLine={false}
              interval="preserveStartEnd"
            />
            <YAxis
              tick={{ fontSize: 11 }}
              axisLine={false}
              tickLine={false}
              tickFormatter={(v) => `$${(v / 1000).toFixed(1)}k`}
              domain={[minBalance - padding, maxBalance + padding]}
              width={52}
            />
            <Tooltip
              labelFormatter={(label) => typeof label === "string" ? formatShortDate(label) : ""}
              formatter={(v) => [formatMoney(v as number, "CAD"), "Balance"]}
              contentStyle={{
                borderRadius: "var(--radius)",
                border: "1px solid var(--border)",
                background: "var(--card)",
                color: "var(--card-foreground)",
                fontSize: 13,
              }}
            />
            <Area
              type="monotone"
              dataKey="balance"
              stroke="var(--chart-1)"
              strokeWidth={2}
              fill="url(#balanceGradient)"
              dot={false}
              activeDot={{ r: 4, fill: "var(--chart-1)" }}
            />
          </AreaChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
