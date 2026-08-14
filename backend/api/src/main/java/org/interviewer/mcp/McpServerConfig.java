package org.interviewer.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The SSE transport, served from inside the existing application.
 *
 * <p>One of only two files permitted to name an SDK type — see {@link McpToolBridge}.
 *
 * <p>Two things a reader should check rather than assume:
 *
 * <ul>
 *   <li><b>{@code /sse} and {@code /mcp/message} must be excluded from the single-origin CORS
 *       mapping and from candidate authentication.</b> An MCP client is not a browser on the
 *       candidate's origin and has no session token; leaving either in place gives a 403 that looks
 *       like a protocol error.</li>
 *   <li>The transport is a {@code RouterFunction}, so it coexists with the {@code @RestController}
 *       endpoints rather than replacing anything.</li>
 * </ul>
 */
@Slf4j
@Configuration
@Profile("!mcp-stdio")
public class McpServerConfig {

    @Bean
    public WebMvcSseServerTransportProvider mcpSseTransport(ObjectMapper objectMapper) {
        return new WebMvcSseServerTransportProvider(objectMapper, "/mcp/message", "/sse");
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(
            WebMvcSseServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    @Bean
    public McpSyncServer mcpSyncServer(WebMvcSseServerTransportProvider transport,
                                       McpToolBridge bridge) {
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("ai-digital-interviewer", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(bridge.specifications())
                .build();
        log.info("MCP SSE server listening: GET /sse, POST /mcp/message");
        return server;
    }
}
