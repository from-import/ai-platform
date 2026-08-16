package org.frostnova.aigateway.conversation.controller;

import jakarta.validation.Valid;
import org.frostnova.aigateway.auth.model.AuthPrincipal;
import org.frostnova.aigateway.auth.web.AuthInterceptor;
import org.frostnova.aigateway.conversation.api.CreateProjectRequest;
import org.frostnova.aigateway.conversation.api.ProjectView;
import org.frostnova.aigateway.conversation.manager.ConversationManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ConversationManager conversationManager;

    public ProjectController(ConversationManager conversationManager) {
        this.conversationManager = conversationManager;
    }

    @GetMapping
    public List<ProjectView> list(
            @RequestAttribute(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE)
            AuthPrincipal principal
    ) {
        return conversationManager.listProjects(principal.getUserId());
    }

    @GetMapping("/{projectId}")
    public ProjectView get(
            @PathVariable String projectId,
            @RequestAttribute(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE)
            AuthPrincipal principal
    ) {
        return conversationManager.getProject(principal.getUserId(), projectId);
    }

    @PostMapping
    public ResponseEntity<ProjectView> create(
            @Valid @RequestBody CreateProjectRequest request,
            @RequestAttribute(AuthInterceptor.AUTH_PRINCIPAL_ATTRIBUTE)
            AuthPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationManager.createProject(principal.getUserId(), request.name()));
    }
}
