import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { NavLink, matchPath, useLocation, useNavigate } from "react-router-dom";
import { getConversations } from "../api/client";
import type { ConversationSummary } from "../api/types";
import { isAbortError } from "../utils/format";

interface ConversationHistoryProps {
  revision: number;
}

function conversationGroup(timestamp: string): string {
  const date = new Date(timestamp);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const itemDay = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const dayDifference = Math.floor((today.getTime() - itemDay.getTime()) / 86_400_000);
  if (dayDifference <= 0) return "Today";
  if (dayDifference === 1) return "Yesterday";
  if (dayDifference < 7) return "Previous 7 days";
  if (dayDifference < 30) return "Previous 30 days";
  return "Older";
}

export function ConversationHistory({ revision }: ConversationHistoryProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const activeConversationId = matchPath(
    "/playground/:conversationId",
    location.pathname,
  )?.params.conversationId;
  const [items, setItems] = useState<ConversationSummary[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const sentinelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    void getConversations(undefined, 20, controller.signal)
      .then((page) => {
        setItems(page.items);
        setNextCursor(page.nextCursor);
        setHasMore(page.hasMore);
      })
      .catch((error: unknown) => {
        if (!isAbortError(error)) {
          setItems([]);
          setNextCursor(null);
          setHasMore(false);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [revision]);

  const loadMore = useCallback(async () => {
    if (!hasMore || !nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await getConversations(nextCursor);
      setItems((current) => {
        const knownIds = new Set(current.map((item) => item.id));
        return [...current, ...page.items.filter((item) => !knownIds.has(item.id))];
      });
      setNextCursor(page.nextCursor);
      setHasMore(page.hasMore);
    } catch {
      // The shared API error notice already explains the failure.
    } finally {
      setLoadingMore(false);
    }
  }, [hasMore, loadingMore, nextCursor]);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !hasMore) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) void loadMore();
      },
      { rootMargin: "120px" },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, loadMore]);

  const groupedItems = useMemo(() => {
    let previousGroup = "";
    return items.map((item) => {
      const group = conversationGroup(item.lastMessageAt);
      const showGroup = group !== previousGroup;
      previousGroup = group;
      return { item, group, showGroup };
    });
  }, [items]);

  return (
    <section className="conversation-history" aria-label="Conversation history">
      <button
        className="new-chat-button"
        type="button"
        onClick={() => navigate(`/playground?new=${Date.now()}`)}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 5v14M5 12h14" />
        </svg>
        <span>New chat</span>
      </button>

      <div className="conversation-history-heading">Chats</div>
      <div className="conversation-history-list">
        {loading ? <div className="conversation-history-state">Loading chats…</div> : null}
        {!loading && items.length === 0 ? (
          <div className="conversation-history-state">No conversations yet</div>
        ) : null}
        {groupedItems.map(({ item, group, showGroup }) => (
          <div key={item.id}>
            {showGroup ? <div className="conversation-history-group">{group}</div> : null}
            <NavLink
              className={`conversation-history-item${activeConversationId === item.id ? " active" : ""}`}
              to={`/playground/${item.id}`}
              title={item.title}
            >
              {item.title}
            </NavLink>
          </div>
        ))}
        <div ref={sentinelRef} className="conversation-history-sentinel">
          {loadingMore ? "Loading more…" : null}
        </div>
      </div>
    </section>
  );
}
