package org.interviewer.entity.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A tool call the model emitted.
 *
 * <p>{@code arguments} stays a {@link JsonNode}: it has not been validated yet, and deserializing
 * into a typed record before validation would turn a repairable schema error into a Jackson
 * exception that carries none of the information the model needs to fix it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {

    private Function function;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Function {
        private String name;
        private JsonNode arguments;
    }

    public static ToolCall of(String name, JsonNode arguments) {
        return new ToolCall(new Function(name, arguments));
    }

    public String toolName() {
        return function == null ? null : function.getName();
    }

    public JsonNode arguments() {
        return function == null ? null : function.getArguments();
    }
}
