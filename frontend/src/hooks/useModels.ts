import { useEffect, useState } from "react";
import { listModels } from "../api/client";
import type { ModelInfo } from "../api/types";
import { errorMessage, isAbortError } from "../utils/format";

interface ModelsState {
  models: ModelInfo[];
  loading: boolean;
  error: string | null;
}

export function useModels(): ModelsState {
  const [state, setState] = useState<ModelsState>({
    models: [],
    loading: true,
    error: null,
  });

  useEffect(() => {
    const controller = new AbortController();

    async function load(): Promise<void> {
      try {
        const models = await listModels(controller.signal);
        const validModels = models.filter((model) => model.provider && model.model);
        if (validModels.length === 0) {
          throw new Error("No models are configured");
        }
        setState({ models: validModels, loading: false, error: null });
      } catch (error) {
        if (!isAbortError(error)) {
          setState({ models: [], loading: false, error: errorMessage(error) });
        }
      }
    }

    void load();
    return () => controller.abort();
  }, []);

  return state;
}
