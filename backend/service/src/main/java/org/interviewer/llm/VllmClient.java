package org.interviewer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.entity.ollama.ChatRequest;
import org.interviewer.entity.ollama.ChatResponse;
import org.interviewer.entity.ollama.ToolCall;
import org.interviewer.utils.LlmProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The model served by vLLM instead of Ollama.
 *
 * <p>Ollama processes roughly one request per loaded model at a time, which is why {@code
 * llmExecutor} is deliberately two threads: a larger pool would only move the queue somewhere with
 * no metrics and no timeouts we control. vLLM batches concurrent requests against one set of
 * weights, which is the change that makes real concurrency possible at all — so this is not a
 * micro-optimisation, it is the difference between a demo and a service.
 *
 * <p><b>A new implementation, not a rewrite.</b> {@link OllamaClient} was extracted so that
 * {@code FakeOllamaClient} could drive the fallback ladder; the same seam takes this. Both are
 * live behind Spring profiles so they can be A/B'd on identical traffic rather than compared from
 * memory.
 *
 * <h2>Three format differences that will bite</h2>
 *
 * <ul>
 *   <li><b>Tool-call arguments arrive as a JSON string, not an object.</b> OpenAI's schema types
 *       {@code function.arguments} as a string containing JSON. Deserialising that straight into
 *       {@link ToolCall} yields a {@code TextNode} where the validator expects an object, and every
 *       call would fail schema validation for a reason that has nothing to do with the model. It is
 *       parsed back here.</li>
 *   <li><b>Sampling options are top-level.</b> Ollama nests them under {@code options}; OpenAI has
 *       {@code temperature} and {@code max_tokens} on the request itself.</li>
 *   <li><b>No timing fields.</b> Ollama returns {@code prompt_eval_duration}, which is the only
 *       honest measure of prompt cost — {@code prompt_eval_count} does not fall when the prefix
 *       cache hits. vLLM's usage block reports tokens only, so {@code promptEvalNanos} stays null
 *       here and the corresponding metric goes quiet. Worth knowing before concluding that
 *       switching backends made prompt evaluation free.</li>
 * </ul>
 *
 * <p><b>Constrained decoding</b> maps to {@code guided_json}, vLLM's extension for exactly this.
 * The grader depends on it: a verdict that can omit a dimension or emit a 7 on a 1-5 scale is a
 * verdict that has to be parsed defensively, which is the failure mode the schema removes. If
 * {@code guided_json} is not honoured, the grader must be re-verified before any of its numbers
 * carry over.
 *
 * <p>Turn it on with {@code --spring.profiles.active=dev,vllm} and point {@code llm.base-url} at
 * the server. Verify tool calling with {@code eval/bench_tools.py} <em>before</em> trusting any
 * latency figure: a different serving stack formats tool calls differently, and 100% argument
 * validity was measured on Ollama.
 */
@Slf4j
@Primary
@Profile("vllm")
@Component
public class VllmClient implements OllamaClient {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient http;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    /** vLLM ignores an unknown key, but sending a real one costs nothing and documents intent. */
    @Value("${llm.vllm.max-tokens:1024}")
    private int maxTokens;

    public VllmClient(OkHttpClient ollamaOkHttpClient,
                      LlmProperties properties,
                      ObjectMapper objectMapper) {
        this.http = ollamaOkHttpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String url = properties.getBaseUrl() + "/v1/chat/completions";
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(toOpenAi(request));
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise the chat request", e);
        }

        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = http.newCall(httpRequest).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.error("vLLM returned HTTP {} for {}: {}", response.code(), url, raw);
                throw new ModelUnavailableException(
                        "vLLM returned HTTP " + response.code() + " from " + url);
            }
            return fromOpenAi(objectMapper.readTree(raw));
        } catch (IOException e) {
            throw new ModelUnavailableException("could not reach vLLM at " + url, e);
        }
    }

    @Override
    public boolean isAvailable() {
        Request ping = new Request.Builder()
                .url(properties.getBaseUrl() + "/v1/models")
                .get()
                .build();
        try (Response response = http.newCall(ping).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    private ObjectNode toOpenAi(ChatRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", request.getModel() == null ? properties.getModel() : request.getModel());
        body.set("messages", objectMapper.valueToTree(request.getMessages()));
        body.put("stream", Boolean.TRUE.equals(request.getStream()));
        body.put("max_tokens", maxTokens);

        // Ollama nests sampling under `options`; OpenAI puts it on the request. Temperature 0 for
        // the grader is not a preference - it is what makes a verdict reproducible enough to
        // compare across runs - so it has to survive the translation.
        Object temperature = request.getOptions() == null
                ? null : request.getOptions().get("temperature");
        body.put("temperature", temperature instanceof Number n
                ? n.doubleValue() : properties.getTemperature());

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.set("tools", objectMapper.valueToTree(request.getTools()));
            body.put("tool_choice", "required");
        }
        if (request.getFormat() != null) {
            // Constrained decoding. `response_format` with a json_schema is the OpenAI standard;
            // vLLM also accepts `guided_json`, which is its own extension.
            //
            // The standard is used here because the extension is NOT portable, and testing found
            // that the hard way: pointed at an OpenAI-compatible server that was not vLLM,
            // `guided_json` was silently ignored and the model answered in prose. Nothing errors
            // when that happens - the request succeeds, the schema simply does not bind - so the
            // grader would have started returning unparseable verdicts on a backend swap, with no
            // signal pointing at the cause.
            //
            // Both fields are sent. vLLM honours either, a plain OpenAI server honours the first,
            // and a server that understands neither still fails loudly at parse time rather than
            // quietly producing an unconstrained verdict.
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("name", "verdict");
            schema.put("strict", true);
            schema.set("schema", objectMapper.valueToTree(request.getFormat()));

            ObjectNode responseFormat = objectMapper.createObjectNode();
            responseFormat.put("type", "json_schema");
            responseFormat.set("json_schema", schema);

            body.set("response_format", responseFormat);
            body.set("guided_json", objectMapper.valueToTree(request.getFormat()));
        }
        return body;
    }

    private ChatResponse fromOpenAi(JsonNode root) {
        ChatResponse response = new ChatResponse();
        response.setModel(root.path("model").asText(null));
        response.setDone(true);

        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");

        ChatMessage reply = new ChatMessage();
        reply.setRole(message.path("role").asText("assistant"));
        reply.setContent(message.path("content").isNull()
                ? "" : message.path("content").asText(""));

        JsonNode calls = message.path("tool_calls");
        if (calls.isArray() && !calls.isEmpty()) {
            reply.setToolCalls(toolCalls((ArrayNode) calls));
        }
        response.setMessage(reply);
        response.setDoneReason(choice.path("finish_reason").asText(null));

        JsonNode usage = root.path("usage");
        if (usage.isObject()) {
            response.setPromptEvalCount(usage.path("prompt_tokens").asInt(0));
            response.setEvalCount(usage.path("completion_tokens").asInt(0));
        }
        // promptEvalNanos stays null: vLLM reports no timing. See the class comment - the metric
        // that actually tracks prompt cost goes quiet on this backend.
        return response;
    }

    /**
     * OpenAI types {@code arguments} as a JSON <em>string</em>; the loop's validator expects an
     * object. Parsing it here rather than at the call site means the rest of the system cannot tell
     * which backend produced the call, which is the whole point of the seam.
     */
    private List<ToolCall> toolCalls(ArrayNode calls) {
        List<ToolCall> parsed = new ArrayList<>();
        for (JsonNode call : calls) {
            JsonNode function = call.path("function");
            String name = function.path("name").asText(null);
            if (name == null) {
                continue;
            }
            JsonNode arguments = function.path("arguments");
            if (arguments.isTextual()) {
                try {
                    arguments = objectMapper.readTree(arguments.asText());
                } catch (Exception e) {
                    // Leave it as the raw text. Schema validation will reject it and rung 2 will
                    // offer a repair - which is a far better outcome than throwing here, because
                    // the model returning unparseable arguments is a case the ladder already
                    // handles and an exception is not.
                    log.warn("vLLM tool call {} had unparseable arguments: {}",
                            name, arguments.asText());
                }
            }
            parsed.add(ToolCall.of(name, arguments));
        }
        return parsed;
    }
}
