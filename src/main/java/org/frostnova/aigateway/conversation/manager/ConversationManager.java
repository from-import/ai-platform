package org.frostnova.aigateway.conversation.manager;

import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.conversation.api.ConversationDetail;
import org.frostnova.aigateway.conversation.api.ConversationItemView;
import org.frostnova.aigateway.conversation.api.ConversationPage;
import org.frostnova.aigateway.conversation.api.ConversationSummary;
import org.frostnova.aigateway.conversation.api.ProjectView;
import org.frostnova.aigateway.conversation.mapper.ChatConversationMapper;
import org.frostnova.aigateway.conversation.mapper.ChatProjectMapper;
import org.frostnova.aigateway.conversation.mapper.ConversationItemMapper;
import org.frostnova.aigateway.conversation.model.ChatConversation;
import org.frostnova.aigateway.conversation.model.ChatProject;
import org.frostnova.aigateway.conversation.model.ConversationItem;
import org.frostnova.aigateway.conversation.model.ConversationItemType;
import org.frostnova.aigateway.conversation.model.ConversationRole;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.Message;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class ConversationManager {

    private static final int MAX_TITLE_CODE_POINTS = 200;
    private static final int MAX_PAGE_SIZE = 50;
    private static final DateTimeFormatter CURSOR_TIME_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ChatConversationMapper chatConversationMapper;
    private final ChatProjectMapper chatProjectMapper;
    private final ConversationItemMapper conversationItemMapper;
    private final ObjectMapper objectMapper;

    public ConversationManager(
            ChatConversationMapper chatConversationMapper,
            ChatProjectMapper chatProjectMapper,
            ConversationItemMapper conversationItemMapper,
            ObjectMapper objectMapper
    ) {
        this.chatConversationMapper = chatConversationMapper;
        this.chatProjectMapper = chatProjectMapper;
        this.conversationItemMapper = conversationItemMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatConversation resolveConversation(Long userId, AppChatRequest request) {
        String conversationId = normalizeIdentifier(request.getConversationId());
        String projectId = normalizeIdentifier(request.getProjectId());
        if (conversationId != null) {
            ChatConversation conversation = chatConversationMapper.findByIdAndUserId(
                    conversationId,
                    userId
            );
            if (conversation == null) {
                throw resourceNotFound("Conversation not found");
            }
            if (projectId != null && !projectId.equals(conversation.getProjectId())) {
                throw resourceNotFound("Conversation not found in project");
            }
            return conversation;
        }

        if (projectId != null && chatProjectMapper.findByIdAndUserId(projectId, userId) == null) {
            throw resourceNotFound("Project not found");
        }

        ChatConversation conversation = ChatConversation.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .projectId(projectId)
                .title(generateTitle(request.getUserMessage()))
                .lastMessageAt(nowUtc())
                .build();
        chatConversationMapper.insert(conversation);
        return conversation;
    }

    public ConversationPage listConversations(Long userId, String encodedCursor, int pageSize) {
        return listConversations(userId, encodedCursor, pageSize, null, false);
    }

    public ConversationPage listConversations(
            Long userId,
            String encodedCursor,
            int pageSize,
            String requestedProjectId,
            boolean unassignedOnly
    ) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BaseException(
                    ErrorCodes.INVALID_REQUEST,
                    "Conversation page size must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        String projectId = normalizeIdentifier(requestedProjectId);
        if (projectId != null && unassignedOnly) {
            throw new BaseException(
                    ErrorCodes.INVALID_REQUEST,
                    "projectId and unassignedOnly cannot be used together"
            );
        }
        if (projectId != null && chatProjectMapper.findByIdAndUserId(projectId, userId) == null) {
            throw resourceNotFound("Project not found");
        }

        ConversationCursor cursor = decodeCursor(encodedCursor);
        List<ChatConversation> rows = chatConversationMapper.findPageByUserId(
                userId,
                cursor == null ? null : cursor.lastMessageAt(),
                cursor == null ? null : cursor.id(),
                projectId,
                unassignedOnly,
                pageSize + 1
        );
        boolean hasMore = rows.size() > pageSize;
        List<ChatConversation> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<ConversationSummary> items = pageRows.stream()
                .map(ConversationSummary::from)
                .toList();
        String nextCursor = hasMore && !pageRows.isEmpty()
                ? encodeCursor(pageRows.getLast())
                : null;
        return new ConversationPage(items, nextCursor, hasMore);
    }

    public List<ProjectView> listProjects(Long userId) {
        return chatProjectMapper.findAllByUserId(userId)
                .stream()
                .map(ProjectView::from)
                .toList();
    }

    public ProjectView getProject(Long userId, String projectId) {
        ChatProject project = findProject(userId, projectId);
        return ProjectView.from(project);
    }

    @Transactional
    public ProjectView createProject(Long userId, String requestedName) {
        String name = requestedName == null ? "" : requestedName.strip();
        if (name.isEmpty()) {
            throw new BaseException(ErrorCodes.INVALID_REQUEST, "Project name is required");
        }
        if (name.length() > 100) {
            throw new BaseException(
                    ErrorCodes.INVALID_REQUEST,
                    "Project name must not exceed 100 characters"
            );
        }

        ChatProject project = ChatProject.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .name(name)
                .build();
        chatProjectMapper.insert(project);
        return ProjectView.from(chatProjectMapper.findByIdAndUserId(project.getId(), userId));
    }

    @Transactional
    public ConversationSummary moveConversation(
            Long userId,
            String conversationId,
            String requestedProjectId
    ) {
        ChatConversation conversation = chatConversationMapper.findByIdAndUserId(
                conversationId,
                userId
        );
        if (conversation == null) {
            throw resourceNotFound("Conversation not found");
        }

        String projectId = normalizeIdentifier(requestedProjectId);
        if (projectId != null) {
            findProject(userId, projectId);
        }
        if (chatConversationMapper.updateProject(conversationId, userId, projectId) != 1) {
            throw resourceNotFound("Conversation not found");
        }
        return ConversationSummary.from(
                chatConversationMapper.findByIdAndUserId(conversationId, userId)
        );
    }

    public ConversationDetail getConversation(Long userId, String conversationId) {
        ChatConversation conversation = chatConversationMapper.findByIdAndUserId(
                conversationId,
                userId
        );
        if (conversation == null) {
            throw resourceNotFound("Conversation not found");
        }

        List<ConversationItemView> items = conversationItemMapper
                .findByConversationIdAndUserId(conversationId, userId)
                .stream()
                .map(this::toItemView)
                .toList();
        return new ConversationDetail(ConversationSummary.from(conversation), items);
    }

    @Transactional
    public void appendMessage(
            ChatConversation conversation,
            ConversationRole role,
            String content
    ) {
        ChatConversation lockedConversation = chatConversationMapper.findByIdAndUserIdForUpdate(
                conversation.getId(),
                conversation.getUserId()
        );
        if (lockedConversation == null) {
            throw resourceNotFound("Conversation not found");
        }

        Integer lastSequenceNo = conversationItemMapper.findLastSequenceNo(
                lockedConversation.getId(),
                lockedConversation.getUserId()
        );
        ConversationItem item = ConversationItem.builder()
                .conversationId(lockedConversation.getId())
                .sequenceNo((lastSequenceNo == null ? 0 : lastSequenceNo) + 1)
                .itemType(ConversationItemType.MESSAGE)
                .role(role)
                .payload(toMessagePayload(content))
                .build();

        conversationItemMapper.insert(item);
        chatConversationMapper.updateLastMessageAt(
                lockedConversation.getId(),
                lockedConversation.getUserId(),
                nowUtc()
        );
    }

    public List<Message> loadMessageHistory(ChatConversation conversation) {
        List<ConversationItem> items = conversationItemMapper.findByConversationIdAndUserId(
                conversation.getId(),
                conversation.getUserId()
        );
        List<Message> messages = new ArrayList<>(items.size());
        for (ConversationItem item : items) {
            if (item.getItemType() != ConversationItemType.MESSAGE) {
                continue;
            }
            messages.add(new Message(
                    item.getRole().name().toLowerCase(Locale.ROOT),
                    readTextContent(item.getPayload())
            ));
        }
        return List.copyOf(messages);
    }

    private String toMessagePayload(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", content
                    ))
            ));
        } catch (JacksonException exception) {
            throw persistenceException("Could not serialize conversation message", exception);
        }
    }

    private String readTextContent(String payload) {
        JsonNode root = readPayload(payload);
        try {
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                throw persistenceException("Conversation message payload is invalid", null);
            }

            StringBuilder text = new StringBuilder();
            for (JsonNode block : content) {
                if (!"text".equals(block.path("type").asText())
                        || !block.path("text").isTextual()) {
                    continue;
                }
                if (!text.isEmpty()) {
                    text.append(System.lineSeparator());
                }
                text.append(block.path("text").asText());
            }
            return text.toString();
        } catch (RuntimeException exception) {
            if (exception instanceof BaseException baseException) {
                throw baseException;
            }
            throw persistenceException("Conversation message payload is invalid", exception);
        }
    }

    private ConversationItemView toItemView(ConversationItem item) {
        return new ConversationItemView(
                item.getId(),
                item.getSequenceNo(),
                item.getItemType(),
                item.getRole(),
                readPayload(item.getPayload()),
                item.getCreatedAt()
        );
    }

    private JsonNode readPayload(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            return root;
        } catch (JacksonException exception) {
            throw persistenceException("Conversation item payload is invalid", exception);
        }
    }

    private String generateTitle(String firstMessage) {
        String title = firstMessage == null
                ? "New conversation"
                : firstMessage.strip().replaceAll("\\s+", " ");
        if (title.isEmpty()) {
            return "New conversation";
        }
        if (title.codePointCount(0, title.length()) <= MAX_TITLE_CODE_POINTS) {
            return title;
        }
        int endIndex = title.offsetByCodePoints(0, MAX_TITLE_CODE_POINTS);
        return title.substring(0, endIndex);
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        return identifier.strip();
    }

    private ChatProject findProject(Long userId, String requestedProjectId) {
        String projectId = normalizeIdentifier(requestedProjectId);
        if (projectId == null) {
            throw resourceNotFound("Project not found");
        }
        ChatProject project = chatProjectMapper.findByIdAndUserId(projectId, userId);
        if (project == null) {
            throw resourceNotFound("Project not found");
        }
        return project;
    }

    private String encodeCursor(ChatConversation conversation) {
        String value = conversation.getLastMessageAt().format(CURSOR_TIME_FORMAT)
                + "|"
                + conversation.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private ConversationCursor decodeCursor(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return null;
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encodedCursor),
                    StandardCharsets.UTF_8
            );
            int separatorIndex = value.lastIndexOf('|');
            if (separatorIndex <= 0 || separatorIndex == value.length() - 1) {
                throw new IllegalArgumentException("Cursor fields are missing");
            }
            return new ConversationCursor(
                    LocalDateTime.parse(value.substring(0, separatorIndex), CURSOR_TIME_FORMAT),
                    value.substring(separatorIndex + 1)
            );
        } catch (RuntimeException exception) {
            throw new BaseException(ErrorCodes.INVALID_REQUEST, "Conversation cursor is invalid");
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private BaseException resourceNotFound(String message) {
        return new BaseException(ErrorCodes.RESOURCE_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    private BaseException persistenceException(String message, Throwable cause) {
        return new BaseException(
                ErrorCodes.INTERNAL_SERVER_ERROR,
                message,
                HttpStatus.INTERNAL_SERVER_ERROR,
                cause
        );
    }

    private record ConversationCursor(LocalDateTime lastMessageAt, String id) {
    }
}
