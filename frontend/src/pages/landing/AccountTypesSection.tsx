// src/pages/landing/AccountTypesSection.tsx

import { Link } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ArrowRight } from "lucide-react";

const accounts = [
  {
    type: "Chequing",
    accent: "border-t-primary",
    badge: "Most popular",
    tagline: "Everyday spending, made simple",
    perks: [
      "No monthly fees",
      "Unlimited Interac e-Transfers",
      "Instant deposits & withdrawals",
      "CDIC insured up to $100K",
    ],
  },
  {
    type: "Savings",
    accent: "border-t-emerald-500",
    badge: "Grow your money",
    tagline: "Your future self will thank you",
    perks: [
      "Competitive interest rate",
      "No minimum balance",
      "Automatic scheduled deposits",
      "CDIC insured up to $100K",
    ],
  },
  {
    type: "TFSA",
    accent: "border-t-violet-500",
    badge: "Tax-free",
    tagline: "Invest and save — keep every dollar",
    perks: [
      "Tax-free growth & withdrawals",
      "Contribution room carries forward",
      "Any Canadian resident can open",
      "CDIC insured up to $100K",
    ],
  },
];

export function AccountTypesSection() {
  return (
    <section id="accounts" className="py-20 bg-muted/30">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-12">
          <h2 className="text-3xl md:text-4xl font-bold mb-4">Choose your account</h2>
          <p className="text-muted-foreground text-lg max-w-xl mx-auto">
            Three account types built for different goals — or open all three.
          </p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {accounts.map(({ type, accent, badge, tagline, perks }) => (
            <Card key={type} className={`border-t-4 ${accent} flex flex-col`}>
              <CardHeader>
                <div className="text-xs font-medium text-primary bg-primary/10 rounded-full px-2.5 py-0.5 w-fit mb-2">
                  {badge}
                </div>
                <CardTitle className="text-xl">{type}</CardTitle>
                <p className="text-sm text-muted-foreground">{tagline}</p>
              </CardHeader>
              <CardContent className="flex flex-col flex-1 gap-4">
                <ul className="space-y-2 flex-1">
                  {perks.map((perk) => (
                    <li key={perk} className="flex items-center gap-2 text-sm">
                      <span className="w-1.5 h-1.5 rounded-full bg-primary flex-shrink-0" />
                      {perk}
                    </li>
                  ))}
                </ul>
                <Button variant="outline" asChild className="w-full mt-2">
                  <Link to="/register">
                    Open {type}
                    <ArrowRight className="ml-2 w-4 h-4" />
                  </Link>
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
