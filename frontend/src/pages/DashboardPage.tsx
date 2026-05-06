// src/pages/DashboardPage.tsx
// Placeholder for now — full version in Part 3.

import { Button } from "@/components/ui/button";
import { useAuth } from "@/auth/useAuth";

export function DashboardPage() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen bg-background text-foreground p-8">
      <header className="flex justify-between items-center max-w-4xl mx-auto mb-8">
        <h1 className="text-2xl font-bold">Banking Platform</h1>
        <div className="flex items-center gap-4">
          <span className="text-sm text-muted-foreground">{user?.email}</span>
          <Button variant="outline" onClick={logout}>Log out</Button>
        </div>
      </header>
      <main className="max-w-4xl mx-auto">
        <p className="text-muted-foreground">
          Logged in as user #{user?.userId} with roles: {user?.roles.join(", ")}.
          Account list, transfers, and history come in Part 3.
        </p>
      </main>
    </div>
  );
}