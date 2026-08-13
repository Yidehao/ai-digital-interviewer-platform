package org.interviewer.entity.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of the {@code tools} array on an Ollama /api/chat request.
 *
 * <p>{@code parameters} is a raw {@link JsonNode} read from {@code tools/{name}.json} rather than a
 * typed structure. The schema files are the contract; modelling them in Java would mean two
 * definitions that can disagree, and the one on the wire would be the one we did not review.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolSpec {

    private String type = "function";

    private Function function;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Function {
        private String name;
        private String description;
        private JsonNode parameters;
    }

    public static ToolSpec of(String name, String description, JsonNode parameters) {
        return new ToolSpec("function", new Function(name, description, parameters));
    }
}
