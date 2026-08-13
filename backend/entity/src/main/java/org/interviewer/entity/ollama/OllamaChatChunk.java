package org.interviewer.entity.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * One NDJSON line of a streaming /api/chat response.
 *
 * <p>Same shape as {@link ChatResponse}; kept separate because the streaming and non-streaming
 * paths have different lifetimes and merging them invites reading a token count off a chunk that
 * does not carry one yet. Only the final chunk has {@code done=true} and the eval counts.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaChatChunk {

    private String model;

    private ChatMessage message;

    private Boolean done;

    @JsonProperty("done_reason")
    private String doneReason;

    @JsonProperty("prompt_eval_count")
    private Integer promptEvalCount;

    @JsonProperty("eval_count")
    private Integer evalCount;

    public String deltaOrEmpty() {
        return message == null || message.getContent() == null ? "" : message.getContent();
    }
}
