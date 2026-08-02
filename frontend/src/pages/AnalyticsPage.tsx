import { useEffect, useState } from "react";
import { getUsageStatistics } from "../api/client";
import type { UsageStatistics } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { formatNumber, isAbortError } from "../utils/format";

export function AnalyticsPage() {
  useDocumentTitle("Analytics");
  const [statistics, setStatistics] = useState<UsageStatistics | null>(null);
  const [status, setStatus] = useState("Loading recorded calls...");

  useEffect(() => {
    const controller = new AbortController();

    async function loadStatistics(): Promise<void> {
      try {
        const result = await getUsageStatistics(controller.signal);
        setStatistics(result);
        setStatus("All recorded provider calls");
      } catch (error) {
        if (!isAbortError(error)) {
          setStatistics(null);
          setStatus("Statistics unavailable");
        }
      }
    }

    void loadStatistics();
    return () => controller.abort();
  }, []);

  const successRate = !statistics || statistics.totalRequests === 0
    ? 0
    : (statistics.successfulRequests / statistics.totalRequests) * 100;

  return (
    <section className="view">
      <PageHeader
        title="Analytics"
        description="Monitor traffic, token consumption and provider reliability."
      />

      <div className="analytics-content">
        <div className="analytics-heading">
          <div>
            <h2>Gateway usage</h2>
            <p>Successful and failed provider calls.</p>
          </div>
          <span>{status}</span>
        </div>

        <div className="statistics">
          <div className="statistic">
            <span>Total Requests</span>
            <strong>{statistics ? formatNumber(statistics.totalRequests) : "-"}</strong>
          </div>
          <div className="statistic">
            <span>Success Rate</span>
            <strong>{statistics ? `${successRate.toFixed(1)}%` : "-"}</strong>
          </div>
          <div className="statistic">
            <span>Total Tokens</span>
            <strong>{statistics ? formatNumber(statistics.totalTokens) : "-"}</strong>
          </div>
          <div className="statistic">
            <span>Average Latency</span>
            <strong>
              {statistics ? `${formatNumber(Math.round(statistics.averageLatencyMs))} ms` : "-"}
            </strong>
          </div>
        </div>
      </div>
    </section>
  );
}
