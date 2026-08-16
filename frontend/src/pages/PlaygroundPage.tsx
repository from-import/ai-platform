import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { createChatCompletionStream, getConversation, getProject } from "../api/client";
import type { ConversationItem, ModelInfo } from "../api/types";
import { MarkdownMessage } from "../components/MarkdownMessage";
import { ModelPicker } from "../components/ModelPicker";
import { PageHeader } from "../components/PageHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { isAbortError } from "../utils/format";

interface PlaygroundPageProps {
  models: ModelInfo[];
  modelsLoading: boolean;
  modelsError: string | null;
  onConversationUpdated: () => void;
}

interface DisplayMessage {
  key: string;
  role: "USER" | "ASSISTANT";
  content: string;
  state?: "waiting" | "error";
}

function textFromItem(item: ConversationItem): string {
  return (item.payload.content || [])
    .filter((block) => block.type === "text" && typeof block.text === "string")
    .map((block) => block.text || "")
    .join("\n");
}

function displayMessages(items: ConversationItem[]): DisplayMessage[] {
  return items
    .filter(
      (item) => item.itemType === "MESSAGE"
        && (item.role === "USER" || item.role === "ASSISTANT"),
    )
    .map((item) => ({
      key: String(item.id),
      role: item.role as "USER" | "ASSISTANT",
      content: textFromItem(item),
    }));
}

export function PlaygroundPage({
  models,
  modelsLoading,
  modelsError,
  onConversationUpdated,
}: PlaygroundPageProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { conversationId, projectId } = useParams<{
    conversationId?: string;
    projectId?: string;
  }>();
  const [prompt, setPrompt] = useState("");
  const [selectedModel, setSelectedModel] = useState<ModelInfo | null>(null);
  const [conversationTitle, setConversationTitle] = useState<string | null>(null);
  const [projectName, setProjectName] = useState<string | null>(null);
  const [projectLoading, setProjectLoading] = useState(false);
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [conversationLoading, setConversationLoading] = useState(false);
  const [requestStatus, setRequestStatus] = useState("");
  const [sending, setSending] = useState(false);
  const conversationEndRef = useRef<HTMLDivElement | null>(null);

  useDocumentTitle(conversationTitle || projectName || "Playground");

  useEffect(() => {
    if (models.length === 0) {
      setSelectedModel(null);
      return;
    }

    const selectionStillExists = selectedModel
      && models.some(
        (model) => model.provider === selectedModel.provider && model.model === selectedModel.model,
      );
    if (!selectionStillExists) {
      setSelectedModel(models[0]);
    }
  }, [models, selectedModel]);

  useEffect(() => {
    const controller = new AbortController();
    if (!projectId) {
      setProjectName(null);
      setProjectLoading(false);
      return () => controller.abort();
    }

    setProjectLoading(true);
    void getProject(projectId, controller.signal)
      .then((project) => setProjectName(project.name))
      .catch((error: unknown) => {
        if (!isAbortError(error)) setProjectName(null);
      })
      .finally(() => {
        if (!controller.signal.aborted) setProjectLoading(false);
      });
    return () => controller.abort();
  }, [projectId]);

  useEffect(() => {
    const controller = new AbortController();
    setPrompt("");
    if (!conversationId) {
      setConversationTitle(null);
      setMessages([]);
      setConversationLoading(false);
      setRequestStatus("");
      return () => controller.abort();
    }

    setConversationLoading(true);
    setRequestStatus("Loading conversation...");
    void getConversation(conversationId, controller.signal)
      .then((detail) => {
        setConversationTitle(detail.conversation.title);
        setMessages(displayMessages(detail.items));
        setRequestStatus("");
      })
      .catch((error: unknown) => {
        if (!isAbortError(error)) {
          setConversationTitle(null);
          setMessages([]);
          setRequestStatus("Conversation could not be loaded");
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setConversationLoading(false);
      });
    return () => controller.abort();
  }, [conversationId, location.search]);

  useEffect(() => {
    conversationEndRef.current?.scrollIntoView({
      behavior: conversationLoading ? "auto" : "smooth",
      block: "end",
    });
  }, [conversationLoading, messages]);

  async function sendRequest(): Promise<void> {
    const userMessage = prompt.trim();
    if (!userMessage) {
      setRequestStatus("Prompt is required");
      return;
    }
    if (!selectedModel) {
      setRequestStatus("No model is available");
      return;
    }

    const turnId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const pendingKey = `assistant-${turnId}`;
    setSending(true);
    setPrompt("");
    setMessages((current) => [
      ...current,
      { key: `user-${turnId}`, role: "USER", content: userMessage },
      { key: pendingKey, role: "ASSISTANT", content: "Thinking…", state: "waiting" },
    ]);
    setRequestStatus("Generating response...");

    try {
      const result = await createChatCompletionStream(
        {
          conversationId,
          projectId,
          provider: selectedModel.provider,
          model: selectedModel.model,
          userMessage,
        },
        (streamedResponse) => {
          if (!streamedResponse.content) return;
          setMessages((current) => current.map((message) => (
            message.key === pendingKey
              ? { ...message, content: streamedResponse.content || "", state: undefined }
              : message
          )));
        },
      );
      setMessages((current) => current.map((message) => (
        message.key === pendingKey
          ? { ...message, content: result.content || "(empty response)", state: undefined }
          : message
      )));
      setRequestStatus("");
      onConversationUpdated();
      if (!conversationId) {
        navigate(
          projectId
            ? `/projects/${projectId}/conversations/${result.conversationId}`
            : `/playground/${result.conversationId}`,
          { replace: true },
        );
      }
    } catch {
      setMessages((current) => current.map((message) => (
        message.key === pendingKey
          ? { ...message, content: "The request could not be completed.", state: "error" }
          : message
      )));
      setRequestStatus("Error");
      onConversationUpdated();
    } finally {
      setSending(false);
    }
  }

  function handlePromptKeyDown(event: KeyboardEvent<HTMLTextAreaElement>): void {
    if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
      event.preventDefault();
      void sendRequest();
    }
  }

  const isEmpty = !conversationLoading && messages.length === 0;
  const composerStatus = projectLoading
    ? "Loading project..."
    : modelsLoading
      ? "Loading models..."
      : modelsError || requestStatus;

  return (
    <section className="view">
      <PageHeader
        title={conversationTitle || projectName || "Playground"}
        description={conversationId
          ? projectName ? `Conversation in ${projectName}` : "Conversation history"
          : projectName ? `Start a new conversation in ${projectName}.` : "Start a new conversation."}
      />

      <div className="playground-body">
        <section className={`conversation${isEmpty ? " empty" : ""}`} aria-live="polite">
          {conversationLoading ? (
            <div className="conversation-empty-state">
              <h2>Loading conversation…</h2>
            </div>
          ) : null}
          {isEmpty ? (
            <div className="conversation-empty-state">
              <h2>{projectName ? `What are we working on in ${projectName}?` : "How can I help?"}</h2>
              <p>
                {projectName
                  ? "This conversation will be organized under the current project."
                  : "Choose a model and send a message to the gateway."}
              </p>
            </div>
          ) : null}
          {!conversationLoading ? messages.map((message) => (
            message.role === "USER" ? (
              <article key={message.key} className="message user-message">
                <span className="message-role">You</span>
                <div>{message.content}</div>
              </article>
            ) : (
              <article key={message.key} className="message assistant-message">
                <span className="assistant-mark">AI</span>
                <div>
                  <span className="message-role">AI Platform</span>
                  <div className={`answer${message.state === "error" ? " error-text" : ""}`}>
                    <MarkdownMessage content={message.content} />
                  </div>
                </div>
              </article>
            )
          )) : null}
          <div ref={conversationEndRef} />
        </section>

        <div className="composer-wrap">
          <div className="composer">
            <label className="sr-only" htmlFor="prompt">
              Prompt
            </label>
            <textarea
              id="prompt"
              value={prompt}
              placeholder="Message the gateway"
              disabled={conversationLoading || projectLoading}
              onChange={(event) => setPrompt(event.target.value)}
              onKeyDown={handlePromptKeyDown}
            />
            <div className="composer-footer">
              <span className={`composer-hint${composerStatus === "Error" ? " error-text" : ""}`}>
                {composerStatus || "Use Ctrl/⌘ + Enter to send"}
              </span>
              <div className="model-control">
                <span className="sr-only">Model</span>
                <ModelPicker
                  models={models}
                  selectedModel={selectedModel}
                  disabled={modelsLoading || Boolean(modelsError) || models.length === 0}
                  onSelect={setSelectedModel}
                />
              </div>
              <button
                className="send-button"
                type="button"
                title="Send request"
                aria-label="Send request"
                disabled={sending || conversationLoading || projectLoading || !selectedModel}
                onClick={() => void sendRequest()}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 19V5M6.5 10.5 12 5l5.5 5.5" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
