// src/pages/landing/LandingNav.tsx

import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";

export function LandingNav() {
  return (
    <nav className="sticky top-0 z-50 border-b bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-8">
          <Link to="/home" className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center">
              <span className="text-primary-foreground text-sm font-bold">M</span>
            </div>
            <span className="text-lg font-semibold tracking-tight">MapleBank</span>
          </Link>
          <div className="hidden md:flex items-center gap-6 text-sm text-muted-foreground">
            <a href="#accounts" className="hover:text-foreground transition-colors">Accounts</a>
            <a href="#features" className="hover:text-foreground transition-colors">Features</a>
            <a href="#why-us" className="hover:text-foreground transition-colors">Why Us</a>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" asChild>
            <Link to="/login">Sign in</Link>
          </Button>
          <Button size="sm" asChild>
            <Link to="/register">Open an account</Link>
          </Button>
        </div>
      </div>
    </nav>
  );
}
