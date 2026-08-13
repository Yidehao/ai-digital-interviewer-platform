package org.interviewer.entity.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A non-streaming /api/chat response.
 *
 * <p>{@code promptEvalCount} and {@code evalCount} are captured on every response, not as a
 * nice-to-have: prompt evaluation is the dominant term in first-token latency on this hardware
 * (74-81 tok/s against 9-10 tok/s for generation), and the six tool schemas are fixed overhead in
 * every single request. Watching prompt tokens grow across a session is how we find out whether
 * the sliding window is actually working.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatResponse {

    private String model;

    private ChatMessage message;

    private Boolean done;

    @JsonProperty("done_reason")
    private String doneReason;

    @JsonProperty("prompt_eval_count")
    private Integer promptEvalCount;

    @JsonProperty("eval_count")
    private Integer evalCount;

    @JsonProperty("prompt_eval_duration")
    private Long promptEvalDurationNanos;

    @JsonProperty("eval_duration")
    private Long evalDurationNanos;

    @JsonProperty("total_duration")
    private Long totalDurationNanos;

    public boolean hasToolCalls() {
        return message != null && message.hasToolCalls();
    }

    public String contentOrEmpty() {
        return message == null || message.getContent() == null ? "" : message.getContent();
    }
}
