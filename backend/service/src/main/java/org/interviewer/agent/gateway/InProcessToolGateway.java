package org.interviewer.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.agent.tool.InterviewTool;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.agent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The production gateway: direct Java calls through {@link ToolRegistry}.
 *
 * <p>Roughly zero overhead, which is the point - the MCP facade exists for interoperability with
 * real clients, not to sit on the interview path.
 */
@Component
public class InProcessToolGateway implements ToolGateway {

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    public InProcessToolGateway(ToolRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> availableTools() {
        return registry.names();
    }

    @Override
    public Optional<ToolDescriptor> describe(String toolName) {
        return registry.find(toolName).map(tool ->
                new ToolDescriptor(tool.name().wireName(), tool.terminal(), tool.timeoutMs()));
    }

    @Override
    public ToolOutcome invoke(String toolName, JsonNode args, ToolContext ctx) {
        InterviewTool<?, ?> tool = registry.find(toolName).orElseThrow(() ->
                new ToolExecutionException("unknown_tool", "no tool named " + toolName));

        long started = System.nanoTime();
        Object result;
        try {
            result = dispatch(tool, args, ctx);
        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException("tool_threw",
                    toolName + " threw " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        long durationMs = (System.nanoTime() - started) / 1_000_000L;

        return new ToolOutcome(toolName, objectMapper.valueToTree(result),
                tool.terminal(), durationMs);
    }

    /**
     * The one place the {@code InterviewTool<A, R>} wildcards have to be defeated. Contained here
     * rather than spread through the loop, and safe because {@code argsType()} is what produced
     * the value being passed.
     */
    @SuppressWarnings("unchecked")
    private <A, R> Object dispatch(InterviewTool<A, R> tool, JsonNode args, ToolContext ctx) {
        A typed;
        try {
            typed = objectMapper.treeToValue(args, tool.argsType());
        } catch (Exception e) {
            // Schema-valid arguments that will not deserialize means the schema and the record
            // have drifted - our bug, and exactly what ToolSchemaDriftTest exists to prevent.
            throw new ToolExecutionException("args_not_deserializable",
                    "schema-valid arguments did not fit " + tool.argsType().getSimpleName()
                            + " - schema and record have drifted: " + e.getMessage(), e);
        }
        return ((InterviewTool<A, R>) tool).execute(typed, ctx);
    }

    /** Convenience for callers holding the enum. */
    public Optional<ToolDescriptor> describe(ToolName toolName) {
        return describe(toolName.wireName());
    }
}
