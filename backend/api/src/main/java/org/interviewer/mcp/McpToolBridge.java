package org.interviewer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.gateway.ToolExecutionException;
import org.interviewer.agent.gateway.ToolGateway;
import org.interviewer.agent.gateway.ToolOutcome;
import org.interviewer.agent.schema.ToolSchemas;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.entity.vo.JobVO;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.service.JobService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exposes the six interview tools to MCP clients.
 *
 * <p><b>This class and {@link McpServerConfig} are the only two files allowed to name an SDK type.</b>
 * The MCP Java SDK renames classes across minor versions — 0.11 alone offers four different
 * {@code McpServer.sync(...)} overloads for four transport shapes — so an upgrade should be a diff
 * against two files rather than a search across the module.
 *
 * <p>The schemas published here are read from the same {@code tools/*.json} files the agent loop
 * validates against and the Ollama request is built from. That is the whole point of them being
 * classpath resources rather than generated from Java: an MCP client, the model, and the validator
 * are all looking at one artifact, so they cannot drift apart. A client that fetches the tool list
 * is seeing exactly what the interviewer sees.
 *
 * <p><b>MCP is a second facade, not the production path.</b> Live interviews dispatch through
 * {@code InProcessToolGateway} at roughly zero cost. Routing them through MCP would add
 * milliseconds and a new failure mode per turn in exchange for nothing the {@link ToolGateway}
 * interface does not already provide. What MCP buys is interoperability — Inspector, Claude
 * Desktop, anything that speaks the protocol — and that is a different thing from a feature.
 */
@Slf4j
@Component
public class McpToolBridge {

    private final ToolGateway gateway;
    private final ToolSchemas schemas;
    private final ObjectMapper objectMapper;
    private final JobService jobService;

    public McpToolBridge(ToolGateway gateway, ToolSchemas schemas, ObjectMapper objectMapper,
                         JobService jobService) {
        this.gateway = gateway;
        this.schemas = schemas;
        this.objectMapper = objectMapper;
        this.jobService = jobService;
    }

    /** One MCP tool specification per registered interview tool. */
    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        List<McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();
        for (String wireName : gateway.availableTools()) {
            ToolName tool = ToolName.fromWireName(wireName).orElse(null);
            if (tool == null) {
                continue;
            }
            JsonNode document = schemas.argsDocument(tool);
            String description = document.path("description").asText("");

            // The published schema is the canonical file, minus the annotation-only keywords that
            // carry nothing a caller can act on.
            if (document.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) document)
                        .remove(List.of("$schema", "$id", "title", "description"));
            }

            McpSchema.Tool mcpTool = new McpSchema.Tool(wireName, description, document.toString());
            specs.add(new McpServerFeatures.SyncToolSpecification(mcpTool,
                    (exchange, arguments) -> call(wireName, arguments)));
        }
        log.info("MCP bridge exposing {} tools: {}", specs.size(), gateway.availableTools());
        return specs;
    }

    /**
     * A detached context, pointed at a real interviewer so the tools have something to work with.
     *
     * <p>Without an {@code interviewerId} a detached {@code fetch_question} can only answer
     * {@code exhausted:true}, which is technically correct and useless as a demonstration - a
     * client exploring the server learns nothing about what the tool does. Resolving the first
     * configured job gives it a real question bank to read.
     *
     * <p>Everything written to this session is still discarded: it is constructed here, used for
     * one call, and dropped. Only persistence differs from a real interview, which is the whole
     * design of {@link ToolContext#detached}.
     */
    private ToolContext detachedContext(String sessionId) {
        ToolContext ctx = ToolContext.detached(sessionId);
        InterviewSession session = ctx.session();
        var jobs = jobService.queryList(1, 1);
        if (!jobs.getRows().isEmpty() && jobs.getRows().get(0) instanceof JobVO vo) {
            session.setJobId(vo.getJobId());
            session.setInterviewerId(vo.getInterviewerId());
        }
        return ctx;
    }

    /**
     * Dispatch one MCP call.
     *
     * <p>An MCP caller has no interview, so it gets a detached {@link ToolContext}: a real session
     * object that is discarded afterwards. Tools behave identically and only persistence differs —
     * which is why {@code finish_interview} reports {@code gradingQueued:false} here. The
     * alternative, a nullable session with null checks in six implementations, puts the same branch
     * in six places and gets it wrong in one.
     */
    private McpSchema.CallToolResult call(String toolName, Map<String, Object> arguments) {
        String sessionId = "mcp-" + java.util.UUID.randomUUID();
        try {
            JsonNode args = objectMapper.valueToTree(arguments == null ? Map.of() : arguments);

            // Validate exactly as the loop does. An MCP client is no more trusted than the model,
            // and a caller that sends a bad argument should get the same readable error the model
            // gets rather than a stack trace.
            ToolName tool = ToolName.fromWireName(toolName).orElseThrow();
            List<String> errors = schemas.validateArgs(tool, args);
            if (!errors.isEmpty()) {
                return new McpSchema.CallToolResult(
                        "invalid arguments for " + toolName + ": " + String.join("; ", errors),
                        true);
            }

            ToolOutcome outcome = gateway.invoke(toolName, args, detachedContext(sessionId));
            return new McpSchema.CallToolResult(outcome.result().toString(), false);

        } catch (ToolExecutionException e) {
            log.warn("MCP call to {} failed: {}", toolName, e.getMessage());
            return new McpSchema.CallToolResult(
                    "{\"error\":\"" + e.code() + "\"}", true);
        } catch (Exception e) {
            log.error("MCP call to {} threw", toolName, e);
            return new McpSchema.CallToolResult("{\"error\":\"internal_error\"}", true);
        }
    }
}
