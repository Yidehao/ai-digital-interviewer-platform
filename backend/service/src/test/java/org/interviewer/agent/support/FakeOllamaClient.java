package org.interviewer.agent.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.entity.ollama.ChatRequest;
import org.interviewer.entity.ollama.ChatResponse;
import org.interviewer.entity.ollama.ToolCall;
import org.interviewer.llm.ModelUnavailableException;
import org.interviewer.llm.OllamaClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * A model that misbehaves exactly on demand.
 *
 * <p>This class is why {@code OllamaClient} is an interface. Every rung of the fallback ladder
 * needs a model that does something specific and wrong - writes prose, emits invalid arguments
 * twice running, names a tool that does not exist, repeats itself, never finishes, disappears
 * mid-interview. None of those are reliably producible from a real model on demand, so a suite
 * built on the real model would be testing the happy path and hoping about the rest.
 *
 * <p>It also runs in microseconds with no GPU, which is what makes it affordable to assert
 * termination on every single rung instead of spot-checking two of them.
 */
public class FakeOllamaClient implements OllamaClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Deque<Supplier<ChatResponse>> script = new ArrayDeque<>();
    private Supplier<ChatResponse> afterScript;
    private boolean available = true;

    /** Every request the loop made, for asserting on prompt shape and tool specs. */
    public final List<ChatRequest> requests = new ArrayList<>();

    public FakeOllamaClient then(Supplier<ChatResponse> response) {
        script.add(response);
        return this;
    }

    /** What to return once the script runs out. Without this, the loop gets prose forever. */
    public FakeOllamaClient thenAlways(Supplier<ChatResponse> response) {
        this.afterScript = response;
        return this;
    }

    public FakeOllamaClient unavailable() {
        this.available = false;
        return this;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        requests.add(request);
        if (!available) {
            throw new ModelUnavailableException("fake: ollama is not running");
        }
        Supplier<ChatResponse> next = script.poll();
        if (next != null) {
            return next.get();
        }
        if (afterScript != null) {
            return afterScript.get();
        }
        return prose("I have nothing further to add.").get();
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public int callCount() {
        return requests.size();
    }

    // ------------------------------------------------------------------ response builders

    /** Rung 1: the model describes what it would do instead of doing it. */
    public static Supplier<ChatResponse> prose(String text) {
        return () -> {
            ChatResponse response = new ChatResponse();
            response.setMessage(ChatMessage.assistant(text));
            response.setDone(true);
            return response;
        };
    }

    public static Supplier<ChatResponse> toolCall(String name, String argsJson) {
        return () -> {
            ChatResponse response = new ChatResponse();
            try {
                response.setMessage(ChatMessage.assistantToolCalls(
                        List.of(ToolCall.of(name, MAPPER.readTree(argsJson)))));
            } catch (Exception e) {
                throw new IllegalStateException("bad test fixture: " + argsJson, e);
            }
            response.setDone(true);
            return response;
        };
    }

    /** Throws on every call - rung 9. */
    public static FakeOllamaClient down() {
        return new FakeOllamaClient().unavailable();
    }
}
