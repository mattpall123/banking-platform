// src/components/AuthNav.tsx

import { Link } from "react-router-dom";
import { ArrowLeft } from "lucide-react";

export function AuthNav() {
  return (
    <nav className="border-b bg-background">
      <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
        <Link to="/home" className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center">
            <span className="text-primary-foreground text-sm font-bold">M</span>
          </div>
          <span className="text-base font-semibold tracking-tight">MapleBank</span>
        </Link>
        <Link
          to="/home"
          className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to home
        </Link>
      </div>
    </nav>
  );
}
