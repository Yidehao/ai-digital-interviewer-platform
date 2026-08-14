package org.interviewer.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The stdio transport: a separate {@code main()} for MCP clients that spawn a process.
 *
 * <p><b>Nothing may write to {@code System.out} in this process.</b> Stdout is the JSON-RPC
 * channel, and a single stray line corrupts every message after it. This application's config
 * therefore has to:
 *
 * <ul>
 *   <li>override {@code mybatis-plus.configuration.log-impl}, which is globally set to
 *       {@code StdOutImpl} and prints every SQL statement to stdout — the single most likely cause
 *       of a broken stdio server in this codebase, and it was already configured that way before
 *       MCP existed;</li>
 *   <li>route logback to stderr;</li>
 *   <li>run with {@link WebApplicationType#NONE}, since there is no servlet container here.</li>
 * </ul>
 *
 * <p>Those overrides live in {@code application-mcp-stdio.yml}. This class is deliberately thin:
 * if it grows logic, that logic will be untested, because a stdio process is awkward to test.
 *
 * <pre>
 *   java -jar api.jar --spring.profiles.active=dev,mcp-stdio
 * </pre>
 */
public final class McpStdioApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(
                org.interviewer.Application.class)
                .web(WebApplicationType.NONE)
                .profiles("mcp-stdio")
                // Banner and startup info go to stdout by default. That alone would corrupt the
                // protocol before a single tool was listed.
                .bannerMode(org.springframework.boot.Banner.Mode.OFF)
                .logStartupInfo(false)
                .run(args);

        McpToolBridge bridge = context.getBean(McpToolBridge.class);
        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

        McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(objectMapper))
                .serverInfo("ai-digital-interviewer", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(bridge.specifications())
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
    }

    private McpStdioApplication() {
    }
}
