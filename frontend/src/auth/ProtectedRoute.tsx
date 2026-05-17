// src/auth/ProtectedRoute.tsx

import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./useAuth";
import type { ReactNode } from "react";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center text-muted-foreground">
        Loading…
      </div>
    );
  }

  if (!user) {
    // Send them to login, but remember where they were trying to go.
    return <Navigate to="/home" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}