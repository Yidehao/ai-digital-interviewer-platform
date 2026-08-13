package org.interviewer.entity.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A request to Ollama /api/chat.
 *
 * <p>{@code options} is a map rather than a typed object because Ollama's option set differs by
 * model and version; an unknown typed field would be dropped silently, which is the worst failure
 * mode for a knob you believe you set.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatRequest {

    private String model;

    private List<ChatMessage> messages;

    private List<ToolSpec> tools;

    private Boolean stream;

    /** "json" or a JSON Schema document, for the grader's constrained decoding. */
    private Object format;

    @JsonProperty("keep_alive")
    private String keepAlive;

    private Map<String, Object> options;

    public static Map<String, Object> options(double temperature, Integer numCtx) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", temperature);
        if (numCtx != null) {
            options.put("num_ctx", numCtx);
        }
        return options;
    }
}
