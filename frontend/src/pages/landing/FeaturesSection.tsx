// src/pages/landing/FeaturesSection.tsx

import { Card, CardContent } from "@/components/ui/card";
import { ShieldCheck, Lock, Send, Clock, FileText, Smartphone } from "lucide-react";

const features = [
  {
    icon: ShieldCheck,
    title: "CDIC Deposit Insurance",
    description: "Your eligible deposits are protected up to $100,000 per insured category by the Canada Deposit Insurance Corporation.",
  },
  {
    icon: Lock,
    title: "Bank-grade Security",
    description: "JWT authentication, bcrypt password hashing, and a tamper-evident audit log protect every action in your account.",
  },
  {
    icon: Send,
    title: "Interac e-Transfer",
    description: "Send and receive money by email instantly. Supports security questions, auto-deposit, and transfer expiry.",
  },
  {
    icon: Clock,
    title: "Scheduled Transfers",
    description: "Set up weekly or monthly recurring payments once and let MapleBank handle the rest — automatically.",
  },
  {
    icon: FileText,
    title: "Monthly Statements",
    description: "Detailed PDF statements are generated every month for each account and available for instant download.",
  },
  {
    icon: Smartphone,
    title: "Digital-first Banking",
    description: "Fully responsive on any device. Manage all your accounts, transfers, and statements from anywhere.",
  },
];

export function FeaturesSection() {
  return (
    <section id="features" className="py-20 bg-background">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-12">
          <h2 className="text-3xl md:text-4xl font-bold mb-4">Everything you need</h2>
          <p className="text-muted-foreground text-lg max-w-xl mx-auto">
            Modern banking features designed for how Canadians actually manage their money.
          </p>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {features.map(({ icon: Icon, title, description }) => (
            <Card key={title} className="border hover:border-primary/40 transition-colors group">
              <CardContent className="pt-6">
                <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center mb-4 group-hover:bg-primary/20 transition-colors">
                  <Icon className="w-5 h-5 text-primary" />
                </div>
                <h3 className="font-semibold mb-2">{title}</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">{description}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
