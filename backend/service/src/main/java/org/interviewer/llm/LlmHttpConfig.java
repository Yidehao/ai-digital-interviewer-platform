package org.interviewer.llm;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.interviewer.utils.LlmProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * One shared OkHttp client for the agent's model calls.
 *
 * <p>Read timeout is generous because a cold first turn genuinely takes seconds: prompt evaluation
 * runs at 74-81 tok/s on this hardware, so a 1.5k-token prompt is ~20 s before the model has
 * produced anything. Connect timeout stays short - if Ollama is not listening we want rung 9
 * quickly, not after two minutes of a candidate staring at a frozen screen.
 */
@Configuration
public class LlmHttpConfig {

    /**
     * Named {@code ollamaOkHttpClient}, not {@code ollamaHttpClient}: the latter is the bean name
     * Spring derives for the {@code @Component OllamaHttpClient} class, and the collision fails
     * the context at startup.
     */
    @Bean
    public OkHttpClient ollamaOkHttpClient(LlmProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .writeTimeout(Duration.ofSeconds(properties.getWriteTimeoutSeconds()))
                .connectionPool(new ConnectionPool(8, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(false)
                .build();
    }
}
