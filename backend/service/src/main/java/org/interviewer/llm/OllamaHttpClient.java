package org.interviewer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.interviewer.entity.ollama.ChatRequest;
import org.interviewer.entity.ollama.ChatResponse;
import org.interviewer.utils.LlmProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.UnknownHostException;

/**
 * The real {@link OllamaClient}, over Ollama's /api/chat.
 *
 * <p>Uses the shared {@code OkHttpClient} bean rather than building one per call, which is what
 * the legacy {@code OllamaTask} does. A new client per call means a new connection pool and a new
 * thread pool per call, and it forfeits connection reuse on the one request whose latency we care
 * about most.
 */
@Slf4j
@Component
public class OllamaHttpClient implements OllamaClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final LlmProperties properties;

    public OllamaHttpClient(OkHttpClient ollamaOkHttpClient,
                            ObjectMapper objectMapper,
                            LlmProperties properties) {
        this.http = ollamaOkHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String url = properties.getBaseUrl() + "/api/chat";
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(request);
        } catch (IOException e) {
            // Our own serialization failing is not a model availability problem.
            throw new IllegalStateException("could not serialize the chat request", e);
        }

        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = http.newCall(httpRequest).execute()) {
            ResponseBody responseBody = response.body();
            String raw = responseBody == null ? "" : responseBody.string();

            if (!response.isSuccessful()) {
                // A 4xx here is our bug - a bad model name, a malformed tools array. It is still
                // "no usable answer from the model", so the loop treats it as rung 9 rather than
                // spinning; the log line is what tells us which of the two it was.
                log.error("Ollama returned HTTP {} for {}: {}", response.code(), url, raw);
                throw new ModelUnavailableException(
                        "Ollama returned HTTP " + response.code());
            }
            return objectMapper.readValue(raw, ChatResponse.class);

        } catch (ConnectException | UnknownHostException | InterruptedIOException e) {
            // InterruptedIOException covers SocketTimeoutException, which is a subclass of it.
            throw new ModelUnavailableException("could not reach Ollama at " + url, e);
        } catch (IOException e) {
            throw new ModelUnavailableException("Ollama call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        Request ping = new Request.Builder()
                .url(properties.getBaseUrl() + "/api/tags")
                .get()
                .build();
        try (Response response = http.newCall(ping).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            log.warn("Ollama not reachable at {}: {}", properties.getBaseUrl(), e.getMessage());
            return false;
        }
    }
}
