package org.interviewer.agent.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.agent.gateway.ToolExecutionException;
import org.interviewer.agent.gateway.ToolGateway;
import org.interviewer.agent.gateway.ToolOutcome;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Tools that behave however a test needs: returning a fixed document, throwing, hanging past their
 * timeout, or returning something that violates their own result schema.
 *
 * <p>Real implementations arrive in Phase 3. What Phase 2 has to prove is that the loop handles
 * each of those behaviours deterministically, which needs tools that produce them on cue.
 */
public class FakeToolGateway implements ToolGateway {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Entry(boolean terminal, long timeoutMs, Function<JsonNode, JsonNode> handler) {
    }

    private final Map<String, Entry> tools = new LinkedHashMap<>();

    /** Every call the loop made, in order. */
    public final List<String> invocations = new ArrayList<>();

    public FakeToolGateway register(ToolName name, String resultJson) {
        return register(name, false, 3_000L, args -> read(resultJson));
    }

    public FakeToolGateway register(ToolName name,
                                    boolean terminal,
                                    long timeoutMs,
                                    Function<JsonNode, JsonNode> handler) {
        tools.put(name.wireName(), new Entry(terminal, timeoutMs, handler));
        return this;
    }

    /** A tool that throws, for rung 4. */
    public FakeToolGateway registerThrowing(ToolName name, RuntimeException toThrow) {
        return register(name, false, 3_000L, args -> {
            throw toThrow;
        });
    }

    /** A tool that outlives its budget, for the loop-enforces-the-timeout test. */
    public FakeToolGateway registerSlow(ToolName name, long sleepMs, long timeoutMs) {
        return register(name, false, timeoutMs, args -> {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return MAPPER.createObjectNode();
        });
    }

    @Override
    public List<String> availableTools() {
        return List.copyOf(tools.keySet());
    }

    @Override
    public Optional<ToolDescriptor> describe(String toolName) {
        Entry entry = tools.get(toolName);
        return entry == null
                ? Optional.empty()
                : Optional.of(new ToolDescriptor(toolName, entry.terminal(), entry.timeoutMs()));
    }

    @Override
    public ToolOutcome invoke(String toolName, JsonNode args, ToolContext ctx) {
        invocations.add(toolName);
        Entry entry = tools.get(toolName);
        if (entry == null) {
            throw new ToolExecutionException("unknown_tool", "no tool named " + toolName);
        }
        long started = System.nanoTime();
        JsonNode result = entry.handler().apply(args);
        return new ToolOutcome(toolName, result, entry.terminal(),
                (System.nanoTime() - started) / 1_000_000L);
    }

    public int invocationsOf(ToolName name) {
        return (int) invocations.stream().filter(n -> n.equals(name.wireName())).count();
    }

    public static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("bad test fixture: " + json, e);
        }
    }
}
