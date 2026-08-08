package org.interviewer.entity.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * One chunk of Ollama /api/generate streaming response (NDJSON line).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaGenerateChunk {

    private String model;

    /** Incremental response text for this chunk */
    private String response;

    /** True when the generation is complete */
    private Boolean done;

    @JsonProperty("done_reason")
    private String doneReason;
}
