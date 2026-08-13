package org.interviewer.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.agent.gateway.InProcessToolGateway;
import org.interviewer.agent.gateway.ToolGateway;
import org.interviewer.agent.schema.ToolSchemas;
import org.interviewer.agent.tool.ToolRegistry;
import org.interviewer.llm.LlmHttpConfig;
import org.interviewer.llm.OllamaClient;
import org.interviewer.llm.OllamaHttpClient;
import org.interviewer.utils.AgentProperties;
import org.interviewer.utils.LlmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent's beans actually wire together.
 *
 * <p>This exists because the unit tests could not have caught what they did not catch: the
 * {@code @Component OllamaHttpClient} class and a {@code @Bean OkHttpClient ollamaHttpClient()}
 * both claim the bean name {@code ollamaHttpClient}, and Spring refuses the context at startup.
 * Every test in this module passed while the application would not boot — which is a fair summary
 * of what a suite of pure unit tests can and cannot tell you.
 *
 * <p>An {@code ApplicationContextRunner} rather than {@code @SpringBootTest}: it starts only these
 * beans, so it needs no MySQL, no Redis and no Ollama, and it runs in milliseconds. The wiring is
 * what is under test, not the database.
 */
class AgentWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LlmHttpConfig.class, AgentConfig.class)
            .withBean(ObjectMapper.class)
            .withBean(LlmProperties.class)
            .withBean(AgentProperties.class)
            .withBean(SystemPromptBuilder.class)
            .withBean(FallbackPlanner.class)
            .withBean(ConversationWindow.class)
            .withBean(ToolRegistry.class)
            .withBean(ToolSchemas.class)
            .withBean(InProcessToolGateway.class)
            .withBean(OllamaHttpClient.class)
            .withBean(InterviewerAgent.class);

    @Test
    @DisplayName("the whole agent context starts, with no bean-name collisions")
    void theContextStarts() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InterviewerAgent.class);
            assertThat(context).hasSingleBean(ToolGateway.class);
            assertThat(context).hasSingleBean(OllamaClient.class);
        });
    }

    @Test
    @DisplayName("the twelve schemas compile during startup, not on first use")
    void schemasCompileAtStartup() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ToolSchemas schemas = context.getBean(ToolSchemas.class);
            // Would throw if @PostConstruct had not run.
            assertThat(schemas.argsDocument(org.interviewer.agent.tool.ToolName.FETCH_QUESTION))
                    .isNotNull();
        });
    }

    @Test
    @DisplayName("an empty tool registry does not stop the context")
    void anEmptyRegistryIsFine() {
        // Phases 1 and 2 genuinely have zero tool implementations. If a required List injection
        // had been used instead of ObjectProvider, the application would not start until Phase 3.
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ToolRegistry.class).size()).isZero();
        });
    }
}
