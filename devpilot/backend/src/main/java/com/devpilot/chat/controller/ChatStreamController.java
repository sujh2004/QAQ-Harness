package com.devpilot.chat.controller;

import com.devpilot.chat.model.ChatStreamRequest;
import com.devpilot.chat.service.ChatStreamService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streaming chat over Server-Sent Events.
 *
 * <p>The response is a live projection of the session's committed event log rather than a token
 * stream: the browser sees the same turns, agent runs and tool calls the audit trail records, and a
 * reconnect with {@code Last-Event-ID} resumes exactly where it left off.
 */
@RestController
public class ChatStreamController {

    private final ChatStreamService chatStreamService;

    /**
     * Creates the controller.
     *
     * @param chatStreamService streaming orchestration
     */
    public ChatStreamController(ChatStreamService chatStreamService) {
        this.chatStreamService = chatStreamService;
    }

    /**
     * Opens a chat stream, starting a turn when a message is supplied.
     *
     * @param request project, session and question
     * @param lastEventId sequence number the client already holds, sent on reconnect
     * @return stream of session events
     */
    @PostMapping(value = "/api/v1/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Valid @RequestBody ChatStreamRequest request,
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        return chatStreamService.open(request, lastEventId == null ? 0 : lastEventId);
    }
}
