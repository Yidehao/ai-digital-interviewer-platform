package org.interviewer.utils;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ollama configuration (local ollama serve).
 */
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaConfig {

    /** Base URL of ollama serve, e.g. http://localhost:11434 */
    private String baseUrl = "http://localhost:11434";

    /** Model name for generate API, e.g. llama3.2, mistral */
    private String model = "llama3.2";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
