import { useEffect, useMemo, useRef, useState } from "react";
import type { ModelInfo } from "../api/types";
import { formatProvider } from "../utils/format";

interface ModelPickerProps {
  models: ModelInfo[];
  selectedModel: ModelInfo | null;
  disabled?: boolean;
  onSelect: (model: ModelInfo) => void;
}

function groupModels(models: ModelInfo[]): Map<string, ModelInfo[]> {
  const groups = new Map<string, ModelInfo[]>();
  for (const model of models) {
    const providerModels = groups.get(model.provider) ?? [];
    providerModels.push(model);
    groups.set(model.provider, providerModels);
  }
  return groups;
}

export function ModelPicker({
  models,
  selectedModel,
  disabled = false,
  onSelect,
}: ModelPickerProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [activeProvider, setActiveProvider] = useState<string | null>(null);
  const modelsByProvider = useMemo(() => groupModels(models), [models]);

  useEffect(() => {
    if (!open) {
      return;
    }

    function handlePointerDown(event: PointerEvent): void {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
        setActiveProvider(null);
      }
    }

    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === "Escape") {
        setOpen(false);
        setActiveProvider(null);
        triggerRef.current?.focus();
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  function toggleMenu(): void {
    setOpen((currentlyOpen) => {
      if (currentlyOpen) {
        setActiveProvider(null);
      }
      return !currentlyOpen;
    });
  }

  function selectModel(model: ModelInfo): void {
    onSelect(model);
    setOpen(false);
    setActiveProvider(null);
  }

  const label = selectedModel
    ? `${formatProvider(selectedModel.provider)} · ${selectedModel.model}`
    : disabled
      ? "Models unavailable"
      : "Choose a model";
  const activeModels = activeProvider ? modelsByProvider.get(activeProvider) ?? [] : [];

  return (
    <div className="model-picker" ref={rootRef}>
      <button
        ref={triggerRef}
        className="model-trigger"
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={disabled}
        title={label}
        onClick={toggleMenu}
      >
        <span className="model-trigger-label">{label}</span>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m8 10 4 4 4-4" />
        </svg>
      </button>

      {open && (
        <div className="model-menu" role="menu" onPointerDown={(event) => event.stopPropagation()}>
          {activeProvider === null ? (
            <>
              <div className="model-menu-header">Choose a provider</div>
              {[...modelsByProvider.entries()].map(([provider, providerModels]) => (
                <button
                  key={provider}
                  className="model-menu-item"
                  type="button"
                  role="menuitem"
                  onClick={() => setActiveProvider(provider)}
                >
                  <span className="provider-mark">{provider.slice(0, 2)}</span>
                  <span className="model-menu-item-label">{formatProvider(provider)}</span>
                  <span className="model-menu-item-meta">{providerModels.length} models ›</span>
                </button>
              ))}
            </>
          ) : (
            <>
              <div className="model-menu-header">
                <button
                  className="model-menu-back"
                  type="button"
                  aria-label="Back to providers"
                  onClick={() => setActiveProvider(null)}
                >
                  ‹
                </button>
                <span>{formatProvider(activeProvider)}</span>
              </div>
              {activeModels.map((model) => {
                const selected = selectedModel?.provider === model.provider
                  && selectedModel.model === model.model;
                return (
                  <button
                    key={`${model.provider}/${model.model}`}
                    className={`model-menu-item${selected ? " selected" : ""}`}
                    type="button"
                    role="menuitem"
                    onClick={() => selectModel(model)}
                  >
                    <span className="model-menu-item-label">{model.model}</span>
                    {selected && <span className="model-menu-item-meta">✓</span>}
                  </button>
                );
              })}
            </>
          )}
        </div>
      )}
    </div>
  );
}
