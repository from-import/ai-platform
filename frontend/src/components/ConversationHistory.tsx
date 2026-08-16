import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { NavLink, matchPath, useLocation, useNavigate } from "react-router-dom";
import {
  createProject,
  getConversations,
  getProjects,
  moveConversation,
} from "../api/client";
import type { ConversationSummary, ProjectView } from "../api/types";
import { isAbortError } from "../utils/format";

interface ConversationHistoryProps {
  revision: number;
}

interface ConversationRowProps {
  conversation: ConversationSummary;
  projects: ProjectView[];
  active: boolean;
  to: string;
  moving: boolean;
  onMove: (conversation: ConversationSummary, projectId: string | null) => Promise<void>;
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

function ConversationRow({
  conversation,
  projects,
  active,
  to,
  moving,
  onMove,
}: ConversationRowProps) {
  function moveTo(projectId: string | null, details: HTMLDetailsElement | null): void {
    void onMove(conversation, projectId).finally(() => details?.removeAttribute("open"));
  }

  return (
    <div className={`conversation-history-row${active ? " active" : ""}`}>
      <NavLink className="conversation-history-item" to={to} title={conversation.title}>
        {conversation.title}
      </NavLink>
      <details className="conversation-actions">
        <summary aria-label={`Organize ${conversation.title}`} title="Organize chat">
          ···
        </summary>
        <div className="conversation-actions-menu">
          <span>Move to</span>
          {conversation.projectId ? (
            <button
              type="button"
              disabled={moving}
              onClick={(event) => moveTo(null, event.currentTarget.closest("details"))}
            >
              Chats
            </button>
          ) : null}
          {projects
            .filter((project) => project.id !== conversation.projectId)
            .map((project) => (
              <button
                key={project.id}
                type="button"
                disabled={moving}
                onClick={(event) => moveTo(
                  project.id,
                  event.currentTarget.closest("details"),
                )}
              >
                {project.name}
              </button>
            ))}
          {!conversation.projectId && projects.length === 0 ? (
            <small>Create a project first</small>
          ) : null}
        </div>
      </details>
    </div>
  );
}

export function ConversationHistory({ revision }: ConversationHistoryProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const projectRoute = matchPath("/projects/:projectId/*", location.pathname);
  const projectConversationRoute = matchPath(
    "/projects/:projectId/conversations/:conversationId",
    location.pathname,
  );
  const playgroundConversationRoute = matchPath(
    "/playground/:conversationId",
    location.pathname,
  );
  const activeProjectId = projectRoute?.params.projectId;
  const activeConversationId = projectConversationRoute?.params.conversationId
    || playgroundConversationRoute?.params.conversationId;

  const [projects, setProjects] = useState<ProjectView[]>([]);
  const [items, setItems] = useState<ConversationSummary[]>([]);
  const [projectItems, setProjectItems] = useState<ConversationSummary[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [projectNextCursor, setProjectNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [projectHasMore, setProjectHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [projectLoading, setProjectLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [projectLoadingMore, setProjectLoadingMore] = useState(false);
  const [movingConversationId, setMovingConversationId] = useState<string | null>(null);
  const [showProjectForm, setShowProjectForm] = useState(false);
  const [projectName, setProjectName] = useState("");
  const [creatingProject, setCreatingProject] = useState(false);
  const [localRevision, setLocalRevision] = useState(0);
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const projectSentinelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    void Promise.all([
      getProjects(controller.signal),
      getConversations({ limit: 20, unassignedOnly: true, signal: controller.signal }),
    ])
      .then(([projectRows, page]) => {
        setProjects(projectRows);
        setItems(page.items);
        setNextCursor(page.nextCursor);
        setHasMore(page.hasMore);
      })
      .catch((error: unknown) => {
        if (!isAbortError(error)) {
          setProjects([]);
          setItems([]);
          setNextCursor(null);
          setHasMore(false);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [localRevision, revision]);

  useEffect(() => {
    const controller = new AbortController();
    if (!activeProjectId) {
      setProjectItems([]);
      setProjectNextCursor(null);
      setProjectHasMore(false);
      setProjectLoading(false);
      return () => controller.abort();
    }

    setProjectLoading(true);
    void getConversations({
      limit: 20,
      projectId: activeProjectId,
      signal: controller.signal,
    })
      .then((page) => {
        setProjectItems(page.items);
        setProjectNextCursor(page.nextCursor);
        setProjectHasMore(page.hasMore);
      })
      .catch((error: unknown) => {
        if (!isAbortError(error)) {
          setProjectItems([]);
          setProjectNextCursor(null);
          setProjectHasMore(false);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setProjectLoading(false);
      });
    return () => controller.abort();
  }, [activeProjectId, localRevision, revision]);

  const loadMore = useCallback(async () => {
    if (!hasMore || !nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await getConversations({ cursor: nextCursor, unassignedOnly: true });
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

  const loadMoreProjectChats = useCallback(async () => {
    if (!activeProjectId || !projectHasMore || !projectNextCursor || projectLoadingMore) return;
    setProjectLoadingMore(true);
    try {
      const page = await getConversations({
        cursor: projectNextCursor,
        projectId: activeProjectId,
      });
      setProjectItems((current) => {
        const knownIds = new Set(current.map((item) => item.id));
        return [...current, ...page.items.filter((item) => !knownIds.has(item.id))];
      });
      setProjectNextCursor(page.nextCursor);
      setProjectHasMore(page.hasMore);
    } catch {
      // The shared API error notice already explains the failure.
    } finally {
      setProjectLoadingMore(false);
    }
  }, [activeProjectId, projectHasMore, projectLoadingMore, projectNextCursor]);

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

  useEffect(() => {
    const sentinel = projectSentinelRef.current;
    if (!sentinel || !projectHasMore) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) void loadMoreProjectChats();
      },
      { rootMargin: "120px" },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [loadMoreProjectChats, projectHasMore]);

  const groupedItems = useMemo(() => {
    let previousGroup = "";
    return items.map((item) => {
      const group = conversationGroup(item.lastMessageAt);
      const showGroup = group !== previousGroup;
      previousGroup = group;
      return { item, group, showGroup };
    });
  }, [items]);

  async function handleCreateProject(): Promise<void> {
    const name = projectName.trim();
    if (!name || creatingProject) return;
    setCreatingProject(true);
    try {
      const project = await createProject({ name });
      setProjectName("");
      setShowProjectForm(false);
      setLocalRevision((value) => value + 1);
      navigate(`/projects/${project.id}?new=${Date.now()}`);
    } finally {
      setCreatingProject(false);
    }
  }

  async function handleMoveConversation(
    conversation: ConversationSummary,
    projectId: string | null,
  ): Promise<void> {
    setMovingConversationId(conversation.id);
    try {
      await moveConversation(conversation.id, projectId);
      setLocalRevision((value) => value + 1);
      if (conversation.id === activeConversationId) {
        navigate(projectId
          ? `/projects/${projectId}/conversations/${conversation.id}`
          : `/playground/${conversation.id}`);
      }
    } finally {
      setMovingConversationId(null);
    }
  }

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

      <div className="conversation-history-list">
        <div className="conversation-section-heading">
          <span>Projects</span>
          <button
            type="button"
            aria-label="Create project"
            title="Create project"
            onClick={() => setShowProjectForm((value) => !value)}
          >
            +
          </button>
        </div>

        {showProjectForm ? (
          <form
            className="project-create-form"
            onSubmit={(event) => {
              event.preventDefault();
              void handleCreateProject();
            }}
          >
            <label className="sr-only" htmlFor="project-name">Project name</label>
            <input
              id="project-name"
              value={projectName}
              maxLength={100}
              autoFocus
              placeholder="Project name"
              disabled={creatingProject}
              onChange={(event) => setProjectName(event.target.value)}
            />
            <button type="submit" disabled={!projectName.trim() || creatingProject}>Create</button>
          </form>
        ) : null}

        {!loading && projects.length === 0 ? (
          <div className="conversation-history-state compact">No projects yet</div>
        ) : null}
        {projects.map((project) => {
          const active = project.id === activeProjectId;
          return (
            <div key={project.id} className="project-block">
              <div className={`project-row${active ? " active" : ""}`}>
                <NavLink to={`/projects/${project.id}`} title={project.name}>
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M3.5 7.5h6l1.7 2H20.5v8.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2Z" />
                    <path d="M3.5 9.5v-4a2 2 0 0 1 2-2h4l1.7 2h7.3a2 2 0 0 1 2 2v2" />
                  </svg>
                  <span>{project.name}</span>
                </NavLink>
                <button
                  type="button"
                  aria-label={`New chat in ${project.name}`}
                  title={`New chat in ${project.name}`}
                  onClick={() => navigate(`/projects/${project.id}?new=${Date.now()}`)}
                >
                  +
                </button>
              </div>
              {active ? (
                <div className="project-conversations">
                  {projectLoading ? (
                    <div className="conversation-history-state compact">Loading chats…</div>
                  ) : null}
                  {!projectLoading && projectItems.length === 0 ? (
                    <div className="conversation-history-state compact">No chats in this project</div>
                  ) : null}
                  {projectItems.map((conversation) => (
                    <ConversationRow
                      key={conversation.id}
                      conversation={conversation}
                      projects={projects}
                      active={conversation.id === activeConversationId}
                      to={`/projects/${project.id}/conversations/${conversation.id}`}
                      moving={movingConversationId === conversation.id}
                      onMove={handleMoveConversation}
                    />
                  ))}
                  <div ref={projectSentinelRef} className="conversation-history-sentinel">
                    {projectLoadingMore ? "Loading more…" : null}
                  </div>
                </div>
              ) : null}
            </div>
          );
        })}

        <div className="conversation-history-heading">Chats</div>
        {loading ? <div className="conversation-history-state">Loading chats…</div> : null}
        {!loading && items.length === 0 ? (
          <div className="conversation-history-state compact">No unassigned chats</div>
        ) : null}
        {groupedItems.map(({ item, group, showGroup }) => (
          <div key={item.id}>
            {showGroup ? <div className="conversation-history-group">{group}</div> : null}
            <ConversationRow
              conversation={item}
              projects={projects}
              active={activeConversationId === item.id}
              to={`/playground/${item.id}`}
              moving={movingConversationId === item.id}
              onMove={handleMoveConversation}
            />
          </div>
        ))}
        <div ref={sentinelRef} className="conversation-history-sentinel">
          {loadingMore ? "Loading more…" : null}
        </div>
      </div>
    </section>
  );
}
