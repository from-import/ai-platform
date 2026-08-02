import { useEffect, useState, type KeyboardEvent } from "react";
import { createChatCompletion } from "../api/client";
import type { ModelInfo } from "../api/types";
import { ModelPicker } from "../components/ModelPicker";
import { PageHeader } from "../components/PageHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

interface PlaygroundPageProps {
  models: ModelInfo[];
  modelsLoading: boolean;
  modelsError: string | null;
}

export function PlaygroundPage({ models, modelsLoading, modelsError }: PlaygroundPageProps) {
  useDocumentTitle("Playground");
  const [prompt, setPrompt] = useState("");
  const [selectedModel, setSelectedModel] = useState<ModelInfo | null>(null);
  const [submittedPrompt, setSubmittedPrompt] = useState<string | null>(null);
  const [answer, setAnswer] = useState("");
  const [answerState, setAnswerState] = useState<"idle" | "waiting" | "answer" | "error">("idle");
  const [requestStatus, setRequestStatus] = useState("");
  const [sending, setSending] = useState(false);

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
    if (modelsLoading) {
      setRequestStatus("Loading models...");
    } else if (modelsError) {
      setRequestStatus(modelsError);
    } else {
      setRequestStatus("");
    }
  }, [modelsError, modelsLoading]);

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

    setSending(true);
    setSubmittedPrompt(userMessage);
    setPrompt("");
    setAnswer("Thinking...");
    setAnswerState("waiting");
    setRequestStatus("Generating response...");

    try {
      const result = await createChatCompletion({
        provider: selectedModel.provider,
        model: selectedModel.model,
        userMessage,
      });
      setAnswer(result.content || "(empty response)");
      setAnswerState("answer");
      setRequestStatus("");
    } catch {
      setAnswer("The request could not be completed.");
      setAnswerState("error");
      setRequestStatus("Error");
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

  return (
    <section className="view">
      <PageHeader
        title="Playground"
        description="Test requests across configured providers and models."
      />

      <div className="playground-body">
        <section className={`conversation${submittedPrompt ? "" : " empty"}`} aria-live="polite">
          {submittedPrompt ? (
            <>
              <article className="message user-message">
                <span className="message-role">You</span>
                <div>{submittedPrompt}</div>
              </article>
              <article className="message assistant-message">
                <span className="assistant-mark">AI</span>
                <div>
                  <span className="message-role">AI Platform</span>
                  <div className={`answer${answerState === "error" ? " error-text" : ""}`}>
                    {answer}
                  </div>
                </div>
              </article>
            </>
          ) : (
            <div className="conversation-empty-state">
              <h2>How can I help?</h2>
              <p>Choose a model and send a message to the gateway.</p>
            </div>
          )}
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
              onChange={(event) => setPrompt(event.target.value)}
              onKeyDown={handlePromptKeyDown}
            />
            <div className="composer-footer">
              <span className={`composer-hint${answerState === "error" ? " error-text" : ""}`}>
                {requestStatus || "Use Ctrl/⌘ + Enter to send"}
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
                disabled={sending || !selectedModel}
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
