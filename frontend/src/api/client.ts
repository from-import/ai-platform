import type {
  ApiErrorPayload,
  ChatRequest,
  ChatResponse,
  ModelInfo,
  RequestRecordPage,
  RequestRecordQuery,
  UsageStatistics,
} from "./types";

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, payload: ApiErrorPayload) {
    super(payload.message || `Request failed with HTTP ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.code = payload.code;
  }
}

export interface ApiErrorNotice {
  id: number;
  code: string;
  message: string;
}

let latestApiError: ApiErrorNotice | null = null;
let nextApiErrorId = 1;
const apiErrorListeners = new Set<() => void>();

export function subscribeToApiErrors(listener: () => void): () => void {
  apiErrorListeners.add(listener);
  return () => apiErrorListeners.delete(listener);
}

export function getLatestApiError(): ApiErrorNotice | null {
  return latestApiError;
}

export function clearLatestApiError(): void {
  latestApiError = null;
  notifyApiErrorListeners();
}

function publishApiError(error: ApiError): void {
  latestApiError = {
    id: nextApiErrorId++,
    code: error.code || "REQUEST_FAILED",
    message: error.message,
  };
  notifyApiErrorListeners();
}

function notifyApiErrorListeners(): void {
  apiErrorListeners.forEach((listener) => listener());
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, options);
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    const apiError = new ApiError(0, {
      code: "NETWORK_ERROR",
      message: "Unable to reach the AI Platform service",
    });
    publishApiError(apiError);
    throw apiError;
  }

  const payload = (await response.json().catch(() => ({}))) as T | ApiErrorPayload;

  if (!response.ok) {
    const errorPayload = payload as Partial<ApiErrorPayload>;
    const apiError = new ApiError(response.status, {
      code: errorPayload.code || "REQUEST_FAILED",
      message: errorPayload.message || `Request failed with HTTP ${response.status}`,
    });
    publishApiError(apiError);
    throw apiError;
  }

  return payload as T;
}

export function listModels(signal?: AbortSignal): Promise<ModelInfo[]> {
  return request<ModelInfo[]>("/api/v1/models", { signal });
}

export function createChatCompletion(
  chatRequest: ChatRequest,
  signal?: AbortSignal,
): Promise<ChatResponse> {
  return request<ChatResponse>("/api/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(chatRequest),
    signal,
  });
}

export function getUsageStatistics(signal?: AbortSignal): Promise<UsageStatistics> {
  return request<UsageStatistics>("/api/v1/usage/statistics", { signal });
}

export function getRequestRecords(
  query: RequestRecordQuery,
  signal?: AbortSignal,
): Promise<RequestRecordPage> {
  const parameters = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
  });

  const filters: Array<[string, string]> = [
    ["requestId", query.requestId],
    ["provider", query.provider],
    ["model", query.model],
    ["status", query.status],
    ["requestedFrom", query.requestedFrom],
    ["requestedTo", query.requestedTo],
  ];

  for (const [name, value] of filters) {
    if (value) {
      parameters.set(name, value);
    }
  }

  return request<RequestRecordPage>(`/api/v1/usage/requests?${parameters}`, { signal });
}
