import { useEffect, useMemo, useState, type FormEvent } from "react";
import { getRequestRecords } from "../api/client";
import type {
  ModelInfo,
  RequestRecord,
  RequestRecordPage,
  RequestRecordQuery,
} from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import {
  formatNumber,
  formatProvider,
  formatRequestedAt,
  isAbortError,
} from "../utils/format";

interface RequestLogsPageProps {
  models: ModelInfo[];
}

type FilterValues = Omit<RequestRecordQuery, "page" | "pageSize">;

const EMPTY_FILTERS: FilterValues = {
  requestId: "",
  provider: "",
  model: "",
  status: "",
  requestedFrom: "",
  requestedTo: "",
};

const EMPTY_PAGE: RequestRecordPage = {
  items: [],
  page: 1,
  pageSize: 20,
  totalItems: 0,
  totalPages: 0,
};

function formatRecordTokens(record: RequestRecord): string {
  if (record.totalTokens != null) {
    return formatNumber(record.totalTokens);
  }
  if (record.promptTokens == null && record.completionTokens == null) {
    return "-";
  }
  return formatNumber((record.promptTokens ?? 0) + (record.completionTokens ?? 0));
}

function recordError(record: RequestRecord): string {
  const parts: string[] = [];
  if (record.upstreamStatusCode != null) {
    parts.push(`HTTP ${record.upstreamStatusCode}`);
  }
  if (record.errorCode) {
    parts.push(record.errorCode);
  }
  if (record.errorMessage) {
    parts.push(record.errorMessage);
  }
  return parts.join(" · ") || "-";
}

export function RequestLogsPage({ models }: RequestLogsPageProps) {
  useDocumentTitle("Request Logs");
  const [filters, setFilters] = useState<FilterValues>(EMPTY_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState<FilterValues>(EMPTY_FILTERS);
  const [pageNumber, setPageNumber] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [page, setPage] = useState<RequestRecordPage>(EMPTY_PAGE);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const providers = useMemo(() => [...new Set(models.map((model) => model.provider))], [models]);
  const providerModels = useMemo(
    () => models.filter((model) => model.provider === filters.provider),
    [filters.provider, models],
  );

  useEffect(() => {
    const controller = new AbortController();

    async function load(): Promise<void> {
      setLoading(true);
      setLoadError(null);
      try {
        const result = await getRequestRecords(
          { ...appliedFilters, page: pageNumber, pageSize },
          controller.signal,
        );
        setPage(result);
      } catch (error) {
        if (!isAbortError(error)) {
          setPage({ ...EMPTY_PAGE, page: pageNumber, pageSize });
          setLoadError("The request query could not be completed.");
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => controller.abort();
  }, [appliedFilters, pageNumber, pageSize]);

  function updateFilter<K extends keyof FilterValues>(name: K, value: FilterValues[K]): void {
    setFilters((current) => ({ ...current, [name]: value }));
  }

  function changeProvider(provider: string): void {
    setFilters((current) => ({ ...current, provider, model: "" }));
  }

  function applyFilters(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    setPageNumber(1);
    setAppliedFilters({ ...filters, requestId: filters.requestId.trim() });
  }

  function resetFilters(): void {
    setFilters({ ...EMPTY_FILTERS });
    setAppliedFilters({ ...EMPTY_FILTERS });
    setPageNumber(1);
  }

  const start = page.totalItems === 0 ? 0 : (page.page - 1) * page.pageSize + 1;
  const end = page.totalItems === 0 ? 0 : start + page.items.length - 1;
  const statusText = loading
    ? "Loading requests..."
    : loadError
      ? "Request query failed"
      : `${formatNumber(page.totalItems)} matching requests`;

  return (
    <section className="view view-wide">
      <PageHeader
        title="Request Logs"
        description="Search individual provider calls, token usage and failures."
        aside={<span className="request-log-status">{statusText}</span>}
      />

      <div className="requests-content">
        <form className="request-filters" onSubmit={applyFilters}>
          <label className="filter-field filter-request-id">
            <span>Request ID</span>
            <input
              type="text"
              value={filters.requestId}
              placeholder="Exact request ID"
              onChange={(event) => updateFilter("requestId", event.target.value)}
            />
          </label>
          <label className="filter-field filter-provider">
            <span>Provider</span>
            <select value={filters.provider} onChange={(event) => changeProvider(event.target.value)}>
              <option value="">All providers</option>
              {providers.map((provider) => (
                <option key={provider} value={provider}>
                  {formatProvider(provider)}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-field filter-model">
            <span>Model</span>
            <select
              value={filters.model}
              disabled={!filters.provider}
              onChange={(event) => updateFilter("model", event.target.value)}
            >
              <option value="">{filters.provider ? "All models" : "Choose a provider first"}</option>
              {providerModels.map((model) => (
                <option key={`${model.provider}/${model.model}`} value={model.model}>
                  {model.model}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-field filter-status">
            <span>Status</span>
            <select
              value={filters.status}
              onChange={(event) => updateFilter("status", event.target.value as FilterValues["status"])}
            >
              <option value="">All statuses</option>
              <option value="SUCCESS">Success</option>
              <option value="FAILED">Failed</option>
            </select>
          </label>
          <label className="filter-field filter-date">
            <span>From</span>
            <input
              type="datetime-local"
              value={filters.requestedFrom}
              onChange={(event) => updateFilter("requestedFrom", event.target.value)}
            />
          </label>
          <label className="filter-field filter-date">
            <span>To</span>
            <input
              type="datetime-local"
              value={filters.requestedTo}
              onChange={(event) => updateFilter("requestedTo", event.target.value)}
            />
          </label>
          <div className="filter-actions">
            <button className="filter-button" type="button" onClick={resetFilters}>
              Reset
            </button>
            <button className="filter-button primary" type="submit">
              Search
            </button>
          </div>
        </form>

        <div className="request-table-shell">
          <table className="request-table">
            <thead>
              <tr>
                <th>Requested at</th>
                <th>Request ID</th>
                <th>Provider / Model</th>
                <th>Status</th>
                <th>Tokens</th>
                <th>Latency</th>
                <th>Error</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td className="table-message" colSpan={7}>Loading request records...</td>
                </tr>
              ) : loadError ? (
                <tr>
                  <td className="table-message error-text" colSpan={7}>
                    Requests unavailable: {loadError}
                  </td>
                </tr>
              ) : page.items.length === 0 ? (
                <tr>
                  <td className="table-message" colSpan={7}>
                    No requests match the selected filters.
                  </td>
                </tr>
              ) : (
                page.items.map((record) => {
                  const error = recordError(record);
                  return (
                    <tr key={record.id ?? record.requestId}>
                      <td className="muted-cell">{formatRequestedAt(record.requestedAt)}</td>
                      <td className="request-id-cell" title={record.requestId}>{record.requestId || "-"}</td>
                      <td className="model-cell">
                        <strong>{formatProvider(record.provider)}</strong>
                        <span title={record.model}>{record.model || "-"}</span>
                      </td>
                      <td>
                        <span className={`status-badge${record.resultStatus === "FAILED" ? " failed" : ""}`}>
                          {record.resultStatus || "-"}
                        </span>
                      </td>
                      <td>{formatRecordTokens(record)}</td>
                      <td>{record.latencyMs == null ? "-" : `${formatNumber(record.latencyMs)} ms`}</td>
                      <td className="error-cell muted-cell" title={error}>{error}</td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <div className="request-pagination">
          <span>{page.totalItems === 0 ? "0 requests" : `${formatNumber(start)}–${formatNumber(end)} of ${formatNumber(page.totalItems)}`}</span>
          <span className="request-pagination-spacer" />
          <label>
            Rows
            <select
              className="page-size-select"
              value={pageSize}
              onChange={(event) => {
                setPageSize(Number(event.target.value));
                setPageNumber(1);
              }}
            >
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
            </select>
          </label>
          <button
            className="page-button"
            type="button"
            disabled={pageNumber <= 1 || loading}
            onClick={() => setPageNumber((current) => current - 1)}
          >
            Previous
          </button>
          <span>Page {page.page} of {Math.max(page.totalPages, 1)}</span>
          <button
            className="page-button"
            type="button"
            disabled={page.totalPages === 0 || pageNumber >= page.totalPages || loading}
            onClick={() => setPageNumber((current) => current + 1)}
          >
            Next
          </button>
        </div>
      </div>
    </section>
  );
}
