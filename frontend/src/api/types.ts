export interface ModelInfo {
  provider: string;
  model: string;
}

export interface ChatRequest {
  provider: string;
  model: string;
  userMessage: string;
}

export interface ChatResponse {
  content: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  providerName: string | null;
}

export interface UsageStatistics {
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  averageLatencyMs: number;
}

export type RequestStatus = "SUCCESS" | "FAILED";

export interface RequestRecord {
  id: number;
  userId: number | null;
  requestId: string;
  provider: string;
  model: string;
  resultStatus: RequestStatus;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  latencyMs: number;
  upstreamStatusCode: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  requestedAt: string;
}

export interface RequestRecordPage {
  items: RequestRecord[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface RequestRecordQuery {
  requestId: string;
  provider: string;
  model: string;
  status: "" | RequestStatus;
  requestedFrom: string;
  requestedTo: string;
  page: number;
  pageSize: number;
}

export interface ApiErrorPayload {
  code: string;
  message: string;
}

export type UserRole = "USER" | "ADMIN";

export interface UserInfo {
  id: number;
  username: string;
  displayName: string;
  role: UserRole;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest extends LoginRequest {
  displayName: string;
}

export interface LoginResponse {
  token: string;
  tokenType: "Bearer";
  expiresAt: string;
  user: UserInfo;
}
