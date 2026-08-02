import { useEffect } from "react";

export function useDocumentTitle(pageTitle: string): void {
  useEffect(() => {
    document.title = `${pageTitle} · AI Platform`;
  }, [pageTitle]);
}
