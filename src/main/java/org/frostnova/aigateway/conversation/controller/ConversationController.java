package org.frostnova.aigateway.conversation.controller;

import jakarta.validation.Valid;
import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.web.AuthInterceptor;
import org.frostnova.aigateway.conversation.api.ConversationDetail;
import org.frostnova.aigateway.conversation.api.ConversationPage;
import org.frostnova.aigateway.conversation.api.ConversationSummary;
import org.frostnova.aigateway.conversation.api.MoveConversationRequest;
import org.frostnova.aigateway.conversation.manager.ConversationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationManager conversationManager;

    public ConversationController(ConversationManager conversationManager) {
        this.conversationManager = conversationManager;
    }

    @GetMapping
    public ConversationPage list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "false") boolean unassignedOnly,
            @RequestAttribute(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE)
            AuthPrincipal principal
    ) {
        return conversationManager.listConversations(
                principal.getUserId(),
                cursor,
                limit,
                projectId,
                unassignedOnly
        );
    }

    @GetMapping("/{conversationId}")
    public ConversationDetail get(
            @PathVariable String conversationId,
            @RequestAttribute(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE)
            AuthPrincipal principal
    ) {
        return conversationManager.getConversation(principal.getUserId(), conversationId);
    }

    @PatchMapping("/{conversationId}/project")
    public ConversationSummary moveToProject(
            @PathVariable String conversationId,
            @Valid @RequestBody MoveConversationRequest request,
            @RequestAttribute(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE)
            AuthPrincipal principal
    ) {
        return conversationManager.moveConversation(
                principal.getUserId(),
                conversationId,
                request.projectId()
        );
    }
}
