package com.finsentinel.ai;

import com.finsentinel.common.security.UserSessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiOrchestrationService orchestrationService;
    private final AiToolsService toolsService;

    public AiController(AiOrchestrationService orchestrationService, AiToolsService toolsService) {
        this.orchestrationService = orchestrationService;
        this.toolsService = toolsService;
    }

    @GetMapping("/status")
    public AiOrchestrationService.AiStatusDto status() {
        return orchestrationService.getStatus();
    }

    @GetMapping("/conversations")
    public List<AiOrchestrationService.ConversationSummaryDto> listConversations(@AuthenticationPrincipal UserSessionPrincipal principal) {
        return orchestrationService.listConversations(principal.userId());
    }

    @GetMapping("/conversations/{id}/messages")
    public List<AiOrchestrationService.MessageDto> getMessages(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID id) {
        return orchestrationService.getMessages(principal.userId(), id);
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@AuthenticationPrincipal UserSessionPrincipal principal, @PathVariable UUID id) {
        orchestrationService.deleteConversation(principal.userId(), id);
    }

    @PostMapping("/chat")
    public AiOrchestrationService.ChatResponse chat(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                    @RequestBody AiOrchestrationService.ChatRequest request) {
        return orchestrationService.chat(principal, request);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@AuthenticationPrincipal UserSessionPrincipal principal,
                                 @RequestBody AiOrchestrationService.ChatRequest request) {
        return orchestrationService.streamChat(principal, request);
    }

    @PostMapping("/runs/{runId}/cancel")
    public Map<String, Boolean> cancelRun(@PathVariable UUID runId) {
        return Map.of("cancelled", orchestrationService.cancelRun(runId));
    }

    @PostMapping("/tools/{toolName}")
    public AiToolsService.ToolInvocationResult invokeTool(@AuthenticationPrincipal UserSessionPrincipal principal,
                                                          @PathVariable String toolName,
                                                          @RequestParam(defaultValue = "false") boolean confirmWrite,
                                                          @RequestBody(required = false) Map<String, Object> args) {
        return toolsService.invokeTool(principal, toolName, args != null ? args : Map.of(), confirmWrite);
    }
}
