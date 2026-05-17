// src/pages/landing/HeroSection.tsx

import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { ArrowRight, ShieldCheck } from "lucide-react";

export function HeroSection() {
  return (
    <section className="relative overflow-hidden bg-gradient-to-br from-primary/10 via-background to-background">
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-primary/5 via-transparent to-transparent pointer-events-none" />
      <div className="max-w-6xl mx-auto px-6 py-24 md:py-36">
        <div className="max-w-3xl">
          <div className="inline-flex items-center gap-2 text-xs font-medium text-primary bg-primary/10 border border-primary/20 rounded-full px-3 py-1 mb-6">
            <ShieldCheck className="w-3.5 h-3.5" />
            CDIC insured · Canadian owned
          </div>
          <h1 className="text-4xl md:text-6xl font-bold tracking-tight leading-tight mb-6">
            Banking that works<br />
            <span className="text-primary">for Canadians</span>
          </h1>
          <p className="text-lg md:text-xl text-muted-foreground mb-10 max-w-2xl leading-relaxed">
            Chequing, savings, and TFSA accounts with Interac e-Transfer, scheduled payments,
            monthly statements, and bank-grade security — all in one place.
          </p>
          <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
            <Button size="lg" asChild className="text-base px-8">
              <Link to="/register">
                Open a free account
                <ArrowRight className="ml-2 w-4 h-4" />
              </Link>
            </Button>
            <Button variant="outline" size="lg" asChild className="text-base">
              <Link to="/login">Sign in</Link>
            </Button>
          </div>
          <p className="mt-6 text-xs text-muted-foreground">
            No monthly fees · No minimum balance · Open in minutes
          </p>
        </div>
      </div>

      {/* Decorative card mockup */}
      <div className="hidden lg:block absolute right-0 top-1/2 -translate-y-1/2 -translate-x-8 w-80 opacity-60">
        <div className="bg-primary rounded-2xl p-6 shadow-2xl text-primary-foreground">
          <div className="flex justify-between items-start mb-8">
            <div>
              <p className="text-xs opacity-70 mb-1">Total balance</p>
              <p className="text-2xl font-bold">$24,381.50</p>
            </div>
            <div className="w-8 h-8 rounded-full bg-primary-foreground/20 flex items-center justify-center">
              <span className="text-sm font-bold">M</span>
            </div>
          </div>
          <div className="space-y-1.5 text-xs opacity-70">
            <div className="flex justify-between">
              <span>Chequing ···· 4821</span>
              <span>$8,200.00</span>
            </div>
            <div className="flex justify-between">
              <span>Savings ···· 3310</span>
              <span>$12,181.50</span>
            </div>
            <div className="flex justify-between">
              <span>TFSA ···· 9042</span>
              <span>$4,000.00</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
