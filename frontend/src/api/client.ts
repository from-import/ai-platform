import type {
  ApiErrorPayload,
  ChatRequest,
  ChatResponse,
  CreateProjectRequest,
  ConversationDetail,
  ConversationPage,
  ConversationSummary,
  LoginRequest,
  LoginResponse,
  ModelInfo,
  ProjectView,
  RegisterRequest,
  RequestRecordPage,
  RequestRecordQuery,
  UsageStatistics,
  UserInfo,
} from "./types";
import { clearAuthSession, getAuthToken } from "../auth/session";

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

async function request<T>(
  path: string,
  options?: RequestInit,
  publishErrors = true,
): Promise<T> {
  const headers = new Headers(options?.headers);
  const token = getAuthToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(path, { ...options, headers });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    const apiError = new ApiError(0, {
      code: "NETWORK_ERROR",
      message: "Unable to reach the AI Platform service",
    });
    if (publishErrors) {
      publishApiError(apiError);
    }
    throw apiError;
  }

  const payload = (await response.json().catch(() => ({}))) as T | ApiErrorPayload;

  if (!response.ok) {
    const errorPayload = payload as Partial<ApiErrorPayload>;
    const apiError = new ApiError(response.status, {
      code: errorPayload.code || "REQUEST_FAILED",
      message: errorPayload.message || `Request failed with HTTP ${response.status}`,
    });
    if (response.status === 401) {
      clearAuthSession();
    }
    if (publishErrors) {
      publishApiError(apiError);
    }
    throw apiError;
  }

  return payload as T;
}

export function login(loginRequest: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(loginRequest),
  }, false);
}

export function register(registerRequest: RegisterRequest): Promise<UserInfo> {
  return request<UserInfo>("/api/v1/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(registerRequest),
  }, false);
}

export function getCurrentUser(signal?: AbortSignal): Promise<UserInfo> {
  return request<UserInfo>("/api/v1/auth/me", { signal }, false);
}

export function logout(): Promise<void> {
  return request<void>("/api/v1/auth/logout", { method: "POST" }, false);
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

export async function createChatCompletionStream(
  chatRequest: ChatRequest,
  onChunk: (response: ChatResponse) => void,
  signal?: AbortSignal,
): Promise<ChatResponse> {
  const headers = new Headers({
    "Content-Type": "application/json",
    Accept: "text/event-stream",
  });
  const token = getAuthToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch("/api/v1/chat/completions/stream", {
      method: "POST",
      headers,
      body: JSON.stringify(chatRequest),
      signal,
    });
  } catch (error) {
    throw toNetworkError(error);
  }

  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as Partial<ApiErrorPayload>;
    const apiError = new ApiError(response.status, {
      code: payload.code || "REQUEST_FAILED",
      message: payload.message || `Request failed with HTTP ${response.status}`,
    });
    if (response.status === 401) {
      clearAuthSession();
    }
    publishApiError(apiError);
    throw apiError;
  }

  if (!response.body) {
    const apiError = new ApiError(0, {
      code: "STREAM_UNAVAILABLE",
      message: "The chat response stream is unavailable",
    });
    publishApiError(apiError);
    throw apiError;
  }

  const result: ChatResponse = {
    conversationId: "",
    content: "",
    promptTokens: null,
    completionTokens: null,
    totalTokens: null,
    providerName: null,
  };
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      buffer = consumeSseEvents(buffer, (eventName, chunk) => {
        if (eventName === "error") {
          throw new ApiError(response.status, {
            code: "LLM_PROVIDER_ERROR",
            message: chunk.content || "The model response failed",
          });
        }
        mergeChatChunk(result, chunk);
        onChunk({ ...result });
      });
      if (done) {
        break;
      }
    }
  } catch (error) {
    if (error instanceof ApiError) {
      publishApiError(error);
      throw error;
    }
    throw toNetworkError(error);
  } finally {
    reader.releaseLock();
  }

  return result;
}

function consumeSseEvents(
  input: string,
  consume: (eventName: string, chunk: ChatResponse) => void,
): string {
  let buffer = input;
  let boundary = buffer.match(/\r?\n\r?\n/);
  while (boundary?.index !== undefined) {
    const block = buffer.slice(0, boundary.index);
    buffer = buffer.slice(boundary.index + boundary[0].length);
    const lines = block.split(/\r?\n/);
    let eventName = "message";
    const dataLines: string[] = [];
    for (const line of lines) {
      if (line.startsWith("event:")) {
        eventName = line.slice("event:".length).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice("data:".length).trimStart());
      }
    }
    if (dataLines.length > 0) {
      consume(eventName, JSON.parse(dataLines.join("\n")) as ChatResponse);
    }
    boundary = buffer.match(/\r?\n\r?\n/);
  }
  return buffer;
}

function mergeChatChunk(target: ChatResponse, chunk: ChatResponse): void {
  if (chunk.conversationId) target.conversationId = chunk.conversationId;
  if (chunk.content) target.content = `${target.content || ""}${chunk.content}`;
  if (chunk.promptTokens != null) target.promptTokens = chunk.promptTokens;
  if (chunk.completionTokens != null) target.completionTokens = chunk.completionTokens;
  if (chunk.totalTokens != null) target.totalTokens = chunk.totalTokens;
  if (chunk.providerName) target.providerName = chunk.providerName;
}

function toNetworkError(error: unknown): Error {
  if (error instanceof DOMException && error.name === "AbortError") {
    return error;
  }
  if (error instanceof ApiError) {
    return error;
  }
  const apiError = new ApiError(0, {
    code: "NETWORK_ERROR",
    message: "Unable to reach the AI Platform service",
  });
  publishApiError(apiError);
  return apiError;
}

export interface ConversationQuery {
  cursor?: string;
  limit?: number;
  projectId?: string;
  unassignedOnly?: boolean;
  signal?: AbortSignal;
}

export function getConversations(query: ConversationQuery = {}): Promise<ConversationPage> {
  const parameters = new URLSearchParams({ limit: String(query.limit ?? 20) });
  if (query.cursor) {
    parameters.set("cursor", query.cursor);
  }
  if (query.projectId) {
    parameters.set("projectId", query.projectId);
  }
  if (query.unassignedOnly) {
    parameters.set("unassignedOnly", "true");
  }
  return request<ConversationPage>(`/api/v1/conversations?${parameters}`, {
    signal: query.signal,
  });
}

export function getConversation(
  conversationId: string,
  signal?: AbortSignal,
): Promise<ConversationDetail> {
  return request<ConversationDetail>(
    `/api/v1/conversations/${encodeURIComponent(conversationId)}`,
    { signal },
  );
}

export function getProjects(signal?: AbortSignal): Promise<ProjectView[]> {
  return request<ProjectView[]>("/api/v1/projects", { signal });
}

export function getProject(projectId: string, signal?: AbortSignal): Promise<ProjectView> {
  return request<ProjectView>(`/api/v1/projects/${encodeURIComponent(projectId)}`, { signal });
}

export function createProject(project: CreateProjectRequest): Promise<ProjectView> {
  return request<ProjectView>("/api/v1/projects", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(project),
  });
}

export function moveConversation(
  conversationId: string,
  projectId: string | null,
): Promise<ConversationSummary> {
  return request<ConversationSummary>(
    `/api/v1/conversations/${encodeURIComponent(conversationId)}/project`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ projectId }),
    },
  );
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
