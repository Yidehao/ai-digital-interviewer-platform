package org.interviewer.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.interviewer.agent.tool.ToolContext;

import java.util.List;
import java.util.Optional;

/**
 * How the loop reaches tools.
 *
 * <p>The agent depends on this interface rather than on {@code ToolRegistry} directly, and that
 * decoupling is the substance of the MCP claim: {@code InProcessToolGateway} makes direct Java
 * calls at roughly zero cost, and a hypothetical {@code McpToolGateway} could put the same tools
 * on a remote server without the loop noticing.
 *
 * <p>To be clear about what MCP is for here: <b>MCP is a second facade, not the production path.</b>
 * Routing live interviews through it would add milliseconds and a new failure mode per turn in
 * exchange for nothing this interface does not already provide.
 */
public interface ToolGateway {

    /** Names the model may call. Fed back verbatim when it invents one. */
    List<String> availableTools();

    /** Empty when this gateway does not serve that name - rung 3. */
    Optional<ToolDescriptor> describe(String toolName);

    /**
     * Dispatch one call. Arguments have already been validated against the tool's schema.
     *
     * @throws ToolExecutionException when the tool threw. Timeouts are applied by the caller, not
     *         here - a tool that timed itself out would leave the loop blocked on a tool that
     *         decided not to return.
     */
    ToolOutcome invoke(String toolName, JsonNode args, ToolContext ctx);

    /** What the loop needs to know about a tool without holding the tool. */
    record ToolDescriptor(String name, boolean terminal, long timeoutMs) {
    }
}
