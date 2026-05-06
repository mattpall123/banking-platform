// src/components/EnvBanner.tsx
//
// Tiny footer-style status banner. Polls /actuator/health every 30s and
// shows a green/red dot + the app version. Useful for demos: a recruiter
// can see "yes the backend is alive and reachable from the browser."

import { useQuery } from "@tanstack/react-query";
import { systemApi } from "@/api/system";

export function EnvBanner() {
  const healthQuery = useQuery({
    queryKey: ["system", "health"],
    queryFn: systemApi.health,
    refetchInterval: 30_000,    // every 30s
    retry: false,
  });

  const infoQuery = useQuery({
    queryKey: ["system", "info"],
    queryFn: systemApi.info,
  });

  const status = healthQuery.data?.status;
  const isUp = status === "UP";
  const version = infoQuery.data?.app?.version ?? "unknown";

  return (
    <div className="border-t bg-muted/30">
      <div className="max-w-5xl mx-auto px-6 py-2 flex items-center justify-between text-xs text-muted-foreground">
        <div className="flex items-center gap-2">
          <span
            className={`inline-block w-2 h-2 rounded-full ${
              isUp ? "bg-emerald-500" : healthQuery.isLoading ? "bg-amber-500" : "bg-rose-500"
            }`}
            aria-label={`Backend status: ${status ?? "checking"}`}
          />
          <span>
            Backend: {healthQuery.isLoading ? "checking…" : status ?? "unreachable"}
          </span>
        </div>
       <div className="flex items-center gap-4">
          <span>v{version}</span><a
          
            href="http://localhost:8080/actuator/prometheus"
            target="_blank"
            rel="noreferrer"
            className="hover:underline"
          >
            metrics
          </a>
        </div>
      </div>
    </div>
  );
}