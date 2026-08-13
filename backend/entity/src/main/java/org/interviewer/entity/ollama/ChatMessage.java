package org.interviewer.entity.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One message in the /api/chat conversation.
 *
 * <p>These are what goes <em>to the model</em>. They are deliberately a different structure from
 * {@code Turn}, which is the transcript the grader reads. Keeping them separate is the whole of
 * the grader-isolation claim: there is no filtering step to get wrong, because the grader is never
 * handed this list at all.
 *
 * <p>Nulls are omitted on serialization - Ollama is happier without a null {@code tool_calls} on
 * every user message, and an unchanging message shape matters for prefix-cache stability.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {

    /** system, user, assistant, or tool. */
    private String role;

    private String content;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    /** Set on tool-result messages so the model can tell which call is being answered. */
    @JsonProperty("tool_name")
    private String toolName;

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", "", toolCalls, null);
    }

    /** A tool result. {@code content} is the serialized result document. */
    public static ChatMessage toolResult(String toolName, String content) {
        return new ChatMessage("tool", content, null, toolName);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
