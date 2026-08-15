package org.frostnova.aigateway.conversation.mapper;

import org.frostnova.aigateway.auth.api.RegisterRequest;
import org.frostnova.aigateway.auth.api.UserView;
import org.frostnova.aigateway.auth.service.AuthService;
import org.frostnova.aigateway.conversation.model.ChatConversation;
import org.frostnova.aigateway.conversation.model.ChatProject;
import org.frostnova.aigateway.conversation.model.ConversationItem;
import org.frostnova.aigateway.conversation.model.ConversationItemType;
import org.frostnova.aigateway.conversation.model.ConversationRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ConversationMapperTests {

    @Autowired
    private ChatProjectMapper projectMapper;

    @Autowired
    private ChatConversationMapper conversationMapper;

    @Autowired
    private ConversationItemMapper itemMapper;

    @Autowired
    private AuthService authService;

    @Test
    void createsReadsRenamesAndDeletesProjectWithinUserBoundary() {
        UserView owner = register("project-owner");
        UserView otherUser = register("project-other");
        ChatProject project = project(owner.id(), "Java learning");

        assertThat(projectMapper.insert(project)).isEqualTo(1);
        assertThat(projectMapper.findByIdAndUserId(project.getId(), owner.id()).getName())
                .isEqualTo("Java learning");
        assertThat(projectMapper.findByIdAndUserId(project.getId(), otherUser.id())).isNull();
        assertThat(projectMapper.findAllByUserId(owner.id()))
                .extracting(ChatProject::getId)
                .containsExactly(project.getId());

        assertThat(projectMapper.updateName(project.getId(), otherUser.id(), "Forbidden"))
                .isZero();
        assertThat(projectMapper.updateName(project.getId(), owner.id(), "Java advanced"))
                .isEqualTo(1);
        assertThat(projectMapper.findByIdAndUserId(project.getId(), owner.id()).getName())
                .isEqualTo("Java advanced");

        assertThat(projectMapper.deleteByIdAndUserId(project.getId(), otherUser.id())).isZero();
        assertThat(projectMapper.deleteByIdAndUserId(project.getId(), owner.id())).isEqualTo(1);
        assertThat(projectMapper.findByIdAndUserId(project.getId(), owner.id())).isNull();
    }

    @Test
    void createsQueriesAndMovesConversations() {
        UserView owner = register("conversation-owner");
        UserView otherUser = register("conversation-other");
        ChatProject project = project(owner.id(), "Backend");
        ChatProject otherProject = project(otherUser.id(), "Private");
        projectMapper.insert(project);
        projectMapper.insert(otherProject);

        LocalDateTime firstActivity = LocalDateTime.of(2026, 8, 15, 9, 0);
        ChatConversation first = conversation(owner.id(), project.getId(), "Spring transactions", firstActivity);
        ChatConversation second = conversation(owner.id(), null, "MyBatis mapping", firstActivity.plusMinutes(1));

        assertThat(conversationMapper.insert(first)).isEqualTo(1);
        assertThat(conversationMapper.insert(second)).isEqualTo(1);
        assertThat(conversationMapper.findByIdAndUserId(first.getId(), otherUser.id())).isNull();
        assertThat(conversationMapper.findRecentByUserId(owner.id(), 10))
                .extracting(ChatConversation::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(conversationMapper.findRecentByProjectId(owner.id(), project.getId(), 10))
                .extracting(ChatConversation::getId)
                .containsExactly(first.getId());
        assertThat(conversationMapper.findRecentWithoutProject(owner.id(), 10))
                .extracting(ChatConversation::getId)
                .containsExactly(second.getId());

        assertThat(conversationMapper.updateProject(second.getId(), owner.id(), otherProject.getId()))
                .isZero();
        assertThat(conversationMapper.updateProject(second.getId(), owner.id(), project.getId()))
                .isEqualTo(1);
        assertThat(conversationMapper.updateTitle(second.getId(), owner.id(), "MyBatis internals"))
                .isEqualTo(1);

        ChatConversation updated = conversationMapper.findByIdAndUserId(second.getId(), owner.id());
        assertThat(updated.getProjectId()).isEqualTo(project.getId());
        assertThat(updated.getTitle()).isEqualTo("MyBatis internals");
    }

    @Test
    void appendsAndReadsItemsInConversationOrder() {
        UserView owner = register("item-owner");
        UserView otherUser = register("item-other");
        ChatConversation conversation = conversation(
                owner.id(),
                null,
                "Greeting",
                LocalDateTime.of(2026, 8, 15, 10, 0)
        );
        conversationMapper.insert(conversation);

        ConversationItem assistantItem = item(
                conversation.getId(),
                2,
                ConversationRole.ASSISTANT,
                "{\"content\":[{\"type\":\"text\",\"text\":\"Hello!\"}]}"
        );
        ConversationItem userItem = item(
                conversation.getId(),
                1,
                ConversationRole.USER,
                "{\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}]}"
        );

        assertThat(itemMapper.insert(assistantItem)).isEqualTo(1);
        assertThat(itemMapper.insert(userItem)).isEqualTo(1);
        assertThat(assistantItem.getId()).isNotNull();
        assertThat(itemMapper.findLastSequenceNo(conversation.getId(), owner.id())).isEqualTo(2);
        assertThat(itemMapper.findByConversationIdAndUserId(conversation.getId(), owner.id()))
                .extracting(ConversationItem::getSequenceNo)
                .containsExactly(1, 2);
        assertThat(itemMapper.findByIdAndUserId(userItem.getId(), owner.id()).getRole())
                .isEqualTo(ConversationRole.USER);
        assertThat(itemMapper.findByIdAndUserId(userItem.getId(), otherUser.id())).isNull();
        assertThat(itemMapper.findByConversationIdAndUserId(conversation.getId(), otherUser.id()))
                .isEmpty();
    }

    @Test
    void detachesConversationsWhenProjectIsDeletedAndCascadesItemsWithConversation() {
        UserView owner = register("delete-owner");
        ChatProject project = project(owner.id(), "Temporary");
        projectMapper.insert(project);
        ChatConversation conversation = conversation(
                owner.id(),
                project.getId(),
                "Keep this chat",
                LocalDateTime.of(2026, 8, 15, 11, 0)
        );
        conversationMapper.insert(conversation);
        ConversationItem item = item(
                conversation.getId(),
                1,
                ConversationRole.USER,
                "{\"content\":[{\"type\":\"text\",\"text\":\"Persist me\"}]}"
        );
        itemMapper.insert(item);

        assertThat(projectMapper.deleteByIdAndUserId(project.getId(), owner.id())).isEqualTo(1);
        assertThat(conversationMapper.findByIdAndUserId(conversation.getId(), owner.id()))
                .extracting(ChatConversation::getProjectId)
                .isNull();
        assertThat(itemMapper.findByIdAndUserId(item.getId(), owner.id())).isNotNull();

        assertThat(conversationMapper.deleteByIdAndUserId(conversation.getId(), owner.id()))
                .isEqualTo(1);
        assertThat(itemMapper.findByIdAndUserId(item.getId(), owner.id())).isNull();
    }

    private UserView register(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return authService.register(new RegisterRequest(
                prefix + "-" + suffix,
                "correct-password",
                prefix
        ));
    }

    private ChatProject project(Long userId, String name) {
        return ChatProject.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .name(name)
                .build();
    }

    private ChatConversation conversation(
            Long userId,
            String projectId,
            String title,
            LocalDateTime lastMessageAt
    ) {
        return ChatConversation.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .projectId(projectId)
                .title(title)
                .lastMessageAt(lastMessageAt)
                .build();
    }

    private ConversationItem item(
            String conversationId,
            int sequenceNo,
            ConversationRole role,
            String payload
    ) {
        return ConversationItem.builder()
                .conversationId(conversationId)
                .sequenceNo(sequenceNo)
                .itemType(ConversationItemType.MESSAGE)
                .role(role)
                .payload(payload)
                .build();
    }
}
