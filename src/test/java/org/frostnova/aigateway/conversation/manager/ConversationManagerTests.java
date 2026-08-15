package org.frostnova.aigateway.conversation.manager;

import org.frostnova.aigateway.auth.api.RegisterRequest;
import org.frostnova.aigateway.auth.api.UserView;
import org.frostnova.aigateway.auth.service.AuthService;
import org.frostnova.aigateway.common.exception.BaseException;
import org.frostnova.aigateway.common.exception.ErrorCodes;
import org.frostnova.aigateway.conversation.mapper.ChatProjectMapper;
import org.frostnova.aigateway.conversation.mapper.ChatConversationMapper;
import org.frostnova.aigateway.conversation.mapper.ConversationItemMapper;
import org.frostnova.aigateway.conversation.model.ChatConversation;
import org.frostnova.aigateway.conversation.model.ChatProject;
import org.frostnova.aigateway.conversation.model.ConversationItemType;
import org.frostnova.aigateway.conversation.model.ConversationRole;
import org.frostnova.aigateway.domain.model.AppChatRequest;
import org.frostnova.aigateway.domain.model.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ConversationManagerTests {

    @Autowired
    private ConversationManager conversationManager;

    @Autowired
    private ConversationItemMapper conversationItemMapper;

    @Autowired
    private ChatProjectMapper projectMapper;

    @Autowired
    private ChatConversationMapper chatConversationMapper;

    @Autowired
    private AuthService authService;

    @Test
    void createsConversationAndPersistsEachMessageInOrder() {
        UserView owner = register("conversation-manager-owner");
        ChatProject project = ChatProject.builder()
                .id(UUID.randomUUID().toString())
                .userId(owner.id())
                .name("Agent project")
                .build();
        projectMapper.insert(project);

        AppChatRequest firstRequest = request(null, project.getId(), "  Explain   AgentLoop  ");
        ChatConversation conversation = conversationManager.resolveConversation(owner.id(), firstRequest);

        conversationManager.appendMessage(
                conversation,
                ConversationRole.USER,
                firstRequest.getUserMessage()
        );
        conversationManager.appendMessage(
                conversation,
                ConversationRole.ASSISTANT,
                "AgentLoop repeatedly lets a model choose and execute actions."
        );

        assertThat(conversation.getId()).isNotBlank();
        assertThat(conversation.getProjectId()).isEqualTo(project.getId());
        assertThat(conversation.getTitle()).isEqualTo("Explain AgentLoop");
        assertThat(conversationManager.loadMessageHistory(conversation))
                .containsExactly(
                        new Message("user", "  Explain   AgentLoop  "),
                        new Message(
                                "assistant",
                                "AgentLoop repeatedly lets a model choose and execute actions."
                        )
                );
        assertThat(conversationItemMapper.findByConversationIdAndUserId(
                conversation.getId(),
                owner.id()
        ))
                .extracting(
                        item -> item.getSequenceNo(),
                        item -> item.getItemType(),
                        item -> item.getRole()
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                1,
                                ConversationItemType.MESSAGE,
                                ConversationRole.USER
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                2,
                                ConversationItemType.MESSAGE,
                                ConversationRole.ASSISTANT
                        )
                );

        AppChatRequest followUp = request(conversation.getId(), null, "Give me an example");
        ChatConversation continuedConversation = conversationManager.resolveConversation(
                owner.id(),
                followUp
        );
        conversationManager.appendMessage(
                continuedConversation,
                ConversationRole.USER,
                followUp.getUserMessage()
        );

        assertThat(continuedConversation.getId()).isEqualTo(conversation.getId());
        assertThat(conversationManager.loadMessageHistory(continuedConversation))
                .extracting(Message::getContent)
                .containsExactly(
                        "  Explain   AgentLoop  ",
                        "AgentLoop repeatedly lets a model choose and execute actions.",
                        "Give me an example"
                );

        assertThat(conversationManager.getConversation(owner.id(), conversation.getId()).items())
                .hasSize(3)
                .first()
                .satisfies(item -> {
                    assertThat(item.role()).isEqualTo(ConversationRole.USER);
                    assertThat(item.payload().path("content").path(0).path("text").asText())
                            .isEqualTo("  Explain   AgentLoop  ");
                });
    }

    @Test
    void pagesConversationsByStableActivityCursor() {
        UserView owner = register("conversation-page-owner");
        UserView otherUser = register("conversation-page-other");
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 15, 12, 0);
        ChatConversation oldest = conversation(owner.id(), "Oldest", baseTime);
        ChatConversation middle = conversation(owner.id(), "Middle", baseTime.plusMinutes(1));
        ChatConversation newest = conversation(owner.id(), "Newest", baseTime.plusMinutes(2));
        ChatConversation privateConversation = conversation(
                otherUser.id(),
                "Not visible",
                baseTime.plusMinutes(3)
        );
        chatConversationMapper.insert(oldest);
        chatConversationMapper.insert(middle);
        chatConversationMapper.insert(newest);
        chatConversationMapper.insert(privateConversation);

        var firstPage = conversationManager.listConversations(owner.id(), null, 2);
        var secondPage = conversationManager.listConversations(
                owner.id(),
                firstPage.nextCursor(),
                2
        );

        assertThat(firstPage.items())
                .extracting(item -> item.id())
                .containsExactly(newest.getId(), middle.getId());
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(secondPage.items())
                .extracting(item -> item.id())
                .containsExactly(oldest.getId());
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();

        assertThatThrownBy(() -> conversationManager.listConversations(owner.id(), "invalid", 2))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCodes.INVALID_REQUEST)
                );
    }

    @Test
    void doesNotExposeAnotherUsersConversationOrProject() {
        UserView owner = register("conversation-manager-private-owner");
        UserView otherUser = register("conversation-manager-private-other");
        ChatConversation conversation = conversationManager.resolveConversation(
                owner.id(),
                request(null, null, "Private message")
        );

        assertThatThrownBy(() -> conversationManager.resolveConversation(
                otherUser.id(),
                request(conversation.getId(), null, "Try to access")
        ))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCodes.RESOURCE_NOT_FOUND)
                );
    }

    private AppChatRequest request(String conversationId, String projectId, String userMessage) {
        AppChatRequest request = new AppChatRequest();
        request.setConversationId(conversationId);
        request.setProjectId(projectId);
        request.setUserMessage(userMessage);
        return request;
    }

    private ChatConversation conversation(Long userId, String title, LocalDateTime activityTime) {
        return ChatConversation.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(title)
                .lastMessageAt(activityTime)
                .build();
    }

    private UserView register(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return authService.register(new RegisterRequest(
                prefix + "-" + suffix,
                "correct-password",
                prefix
        ));
    }
}
