package org.interviewer.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.agent.gateway.ToolGateway;
import org.interviewer.agent.schema.ToolSchemas;
import org.interviewer.agent.support.FakeOllamaClient;
import org.interviewer.agent.support.FakeToolGateway;
import org.interviewer.agent.support.Fixtures;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.entity.agent.FallbackReason;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.llm.OllamaClient;
import org.interviewer.utils.AgentProperties;
import org.interviewer.utils.LlmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One test per rung of the fallback ladder.
 *
 * <p><b>Every test here asserts the same thing at the end: the session reached a terminal state.</b>
 * That repeated assertion is not boilerplate — it <em>is</em> the "the loop never stalls" claim.
 * A claim like that cannot be demonstrated by a demo, because a demo shows one path working; it is
 * demonstrated by showing that eleven different ways of going wrong all end.
 *
 * <p>The second thing each test asserts is that the rung's action was <em>deterministic</em> — a
 * question served, a specific message returned, an interview closed — and that nowhere did the loop
 * respond to a bad turn by asking the model again and hoping.
 */
class FallbackLadderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentProperties agentProperties = new AgentProperties();
    private final LlmProperties llmProperties = new LlmProperties();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private InterviewerAgent agentWith(OllamaClient llm, ToolGateway gateway) {
        return agentWith(llm, gateway, Clock.systemUTC());
    }

    private InterviewerAgent agentWith(OllamaClient llm, ToolGateway gateway, Clock clock) {
        ToolSchemas schemas = ToolSchemas.compiled(mapper);
        return new InterviewerAgent(llm, gateway, schemas, new FallbackPlanner(),
                new ConversationWindow(agentProperties), mapper,
                agentProperties, llmProperties, executor, clock);
    }

    // ------------------------------------------------------------------ rung 1

    @Test
    @DisplayName("rung 1: prose gets exactly one nudge, then the loop moves on without the model")
    void proseOnceIsNudgedOnce() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.prose("I think I should ask about caching next."))
                .then(FakeOllamaClient.toolCall("finish_interview",
                        """
                        {"reason":"complete","closingMessage":"Thanks."}"""));
        FakeToolGateway gateway = Fixtures.happyGateway();
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState().isTerminal()).isTrue();
        assertThat(session.getFallbackCounts()).containsEntry(FallbackReason.NO_TOOL_CALL, 1);
        // Exactly one nudge, never a retry loop.
        assertThat(session.getMessages())
                .filteredOn(m -> "user".equals(m.getRole()))
                .hasSize(1);
    }

    @Test
    @DisplayName("rung 1b: prose twice serves the next question with no model in the path")
    void proseTwiceServesAQuestionItself() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .thenAlways(FakeOllamaClient.prose("Let me consider what to ask."));
        FakeToolGateway gateway = Fixtures.happyGateway();
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState().isTerminal()).isTrue();
        assertThat(session.getFallbackCounts())
                .containsKey(FallbackReason.NO_TOOL_CALL_REPEATED);
        // The interview visibly moved forward without the model choosing anything.
        assertThat(gateway.invocationsOf(ToolName.FETCH_QUESTION)).isGreaterThan(0);
    }

    @Test
    @DisplayName("rung 1b: prose twice with an empty bank closes the interview cleanly")
    void proseTwiceWithNothingLeftToAskCloses() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .thenAlways(FakeOllamaClient.prose("Hmm."));
        FakeToolGateway gateway = new FakeToolGateway()
                .register(ToolName.FETCH_QUESTION, Fixtures.EXHAUSTED_RESULT);
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState()).isEqualTo(SessionState.FINISHED);
        assertThat(session.getClosingMessage()).isNotBlank();
    }

    // ------------------------------------------------------------------ rung 2

    @Test
    @DisplayName("rung 2: invalid arguments get the validation errors back verbatim, once")
    void invalidArgumentsAreRepairedOnce() {
        FakeOllamaClient llm = new FakeOllamaClient()
                // score 9 is outside the 1-5 rubric range.
                .then(FakeOllamaClient.toolCall("score_response",
                        """
                        {"questionId":"q-1","dimension":"depth","score":9,"evidence":"Good."}"""))
                .then(FakeOllamaClient.toolCall("score_response",
                        """
                        {"questionId":"q-1","dimension":"depth","score":3,"evidence":"Good."}"""))
                .then(FakeOllamaClient.toolCall("finish_interview",
                        """
                        {"reason":"complete","closingMessage":"Thanks."}"""));
        FakeToolGateway gateway = Fixtures.happyGateway();
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState().isTerminal()).isTrue();
        assertThat(session.getFallbackCounts()).containsEntry(FallbackReason.INVALID_ARGS, 1);
        // The repair message must name the field, or one repair turn cannot work.
        assertThat(session.getMessages())
                .anySatisfy(m -> assertThat(m.getContent()).contains("score"));
        // The corrected call went through.
        assertThat(gateway.invocationsOf(ToolName.SCORE_RESPONSE)).isEqualTo(1);
    }

    @Test
    @DisplayName("rung 2: a second invalid call demotes to rung 1 rather than repairing again")
    void invalidArgumentsTwiceDemotesToTheDeterministicBranch() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .thenAlways(FakeOllamaClient.toolCall("score_response",
                        """
                        {"questionId":"q-1","dimension":"depth","score":9,"evidence":"Good."}"""));
        FakeToolGateway gateway = Fixtures.happyGateway();
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState().isTerminal()).isTrue();
        // Never dispatched: the arguments never became valid.
        assertThat(gateway.invocationsOf(ToolName.SCORE_RESPONSE)).isZero();
        assertThat(gateway.invocationsOf(ToolName.FETCH_QUESTION)).isGreaterThan(0);
    }

    // ------------------------------------------------------------------ rung 3

    @Test
    @DisplayName("rung 3: an invented tool name comes back with the list of real ones")
    void unknownToolIsAnsweredWithTheAvailableNames() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.toolCall("summarise_candidate", "{}"))
                .then(FakeOllamaClient.toolCall("finish_interview",
                        """
                        {"reason":"complete","closingMessage":"Thanks."}"""));
        FakeToolGateway gateway = Fixtures.happyGateway();
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState().isTerminal()).isTrue();
        assertThat(session.getFallbackCounts()).containsEntry(FallbackReason.UNKNOWN_TOOL, 1);
        assertThat(session.getMessages())
                .anySatisfy(m -> assertThat(m.getContent()).contains("fetch_question"));
    }

    // ------------------------------------------------------------------ rung 4

    @Test
    @DisplayName("rung 4: a throwing tool becomes a structured error, not an exception")
    void aThrowingToolIsReportedAsData() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.toolCall("fetch_question", "{}"))
                .then(FakeOllamaClient.toolCall("finish_interview",
                        """
                        {"reason":"complete","closingMessage":"Thanks."}"""));
        FakeToolGateway gateway = Fixtures.happyGateway()
                .registerThrowing(ToolName.FETCH_QUESTION,
                        new IllegalStateException("question bank is offline"));
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState().isTerminal()).isTrue();
        assertThat(session.getFallbackCounts()).containsEntry(FallbackReason.TOOL_ERROR, 1);
        assertThat(session.getMessages())
                .anySatisfy(m -> assertThat(m.getContent()).contains("tool_failed"));
    }

    @Test
    @DisplayName("rung 4: the loop enforces the timeout, not the tool")
    void aHangingToolIsAbandonedByTheLoop() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.toolCall("fetch_question", "{}"))
                .then(FakeOllamaClient.toolCall("finish_interview",
                        """
                        {"reason":"complete","closingMessage":"Thanks."}"""));
        // Sleeps 10 s under a 200 ms budget. If the tool were responsible for its own timeout,
        // this test would take 10 s - which is exactly the point being made.
        FakeToolGateway gateway = Fixtures.happyGateway()
                .registerSlow(ToolName.FETCH_QUESTION, 10_000L, 200L);
        InterviewSession session = Fixtures.session();

        long started = System.currentTimeMillis();
        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);
        long elapsed = System.currentTimeMillis() - started;

        assertThat(session.getState().isTerminal()).isTrue();
        assertThat(elapsed).isLessThan(5_000L);
        assertThat(session.getMessages())
                .anySatisfy(m -> assertThat(m.getContent()).contains("timeout"));
    }

    // ------------------------------------------------------------------ rung 5

    @Test
    @DisplayName("rung 5: our own malformed result is our bug, and the model is told so")
    void aResultViolatingItsOwnSchemaIsAnInternalError() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.toolCall("finish_interview",
                        """
                        {"reason":"complete","closingMessage":"Thanks."}"""))
                .thenAlways(FakeOllamaClient.prose("."));
        FakeToolGateway gateway = new FakeToolGateway()
                .register(ToolName.FETCH_QUESTION, Fixtures.EXHAUSTED_RESULT)
                .register(ToolName.FINISH_INTERVIEW, true, 3_000L,
                        args -> FakeToolGateway.read(Fixtures.MALFORMED_FINISH_RESULT));
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState().isTerminal()).isTrue();
        assertThat(session.getFallbackCounts())
                .containsEntry(FallbackReason.RESULT_SCHEMA_INVALID, 1);
        // The interview did NOT finish on a malformed finish result - a terminal tool whose
        // result is broken has not demonstrably done its job.
        assertThat(session.getMessages())
                .anySatisfy(m -> assertThat(m.getContent()).contains("tool_internal_error"));
    }

    // ------------------------------------------------------------------ rung 6

    @Test
    @DisplayName("rung 6: the identical call twice running is named and forbidden")
    void repeatingTheSameCallIsRefused() {
        String sameCall = """
                {"questionId":"q-1","dimension":"depth","score":3,"evidence":"Fine."}""";
        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.toolCall("score_response", sameCall))
                .then(FakeOllamaClient.toolCall("score_response", sameCall))
                .then(FakeOllamaClient.toolCall("finish_interview",
                        """
                        {"reason":"complete","closingMessage":"Thanks."}"""));
        FakeToolGateway gateway = Fixtures.happyGateway();
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState().isTerminal()).isTrue();
        assertThat(session.getFallbackCounts()).containsEntry(FallbackReason.REPEATED_CALL, 1);
        // Dispatched once, not twice.
        assertThat(gateway.invocationsOf(ToolName.SCORE_RESPONSE)).isEqualTo(1);
        assertThat(session.getMessages())
                .anySatisfy(m -> assertThat(m.getContent()).contains("repeated_call"));
    }

    // ------------------------------------------------------------------ rung 7

    @Test
    @DisplayName("rung 7: the error budget closes the interview rather than letting it grind on")
    void theErrorBudgetEndsTheInterview() {
        agentProperties.setMaxToolErrors(3);
        FakeOllamaClient llm = new FakeOllamaClient()
                .thenAlways(FakeOllamaClient.toolCall("no_such_tool", "{}"));
        FakeToolGateway gateway = new FakeToolGateway()
                .register(ToolName.FETCH_QUESTION, Fixtures.EXHAUSTED_RESULT);
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState()).isEqualTo(SessionState.FINISHED);
        assertThat(session.getTerminalReason()).isEqualTo(FallbackReason.ERROR_BUDGET);
        assertThat(session.getErrorCount()).isLessThanOrEqualTo(5);
    }

    // ------------------------------------------------------------------ rung 8

    @Test
    @DisplayName("rung 8: a model that never finishes is stopped by the turn budget")
    void theTurnBudgetStopsAModelThatNeverFinishes() {
        agentProperties.setMaxTurns(5);
        FakeOllamaClient llm = new FakeOllamaClient()
                .thenAlways(FakeOllamaClient.toolCall("fetch_question",
                        // Different arguments each time, so rung 6 never fires and only the
                        // budget can end this.
                        "{\"topic\":\"" + System.nanoTime() + "\"}"));
        FakeToolGateway gateway = Fixtures.happyGateway();
        InterviewSession session = Fixtures.session();

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState()).isEqualTo(SessionState.FINISHED);
        assertThat(session.getTerminalReason()).isEqualTo(FallbackReason.BUDGET);
        assertThat(session.getTurnCount()).isLessThanOrEqualTo(6);
    }

    @Test
    @DisplayName("rung 8: the wall clock ends the interview, tested without waiting 45 minutes")
    void theWallClockBudgetEndsTheInterview() {
        Instant start = Instant.parse("2026-08-13T09:00:00Z");
        // A clock that jumps an hour on its second reading. This is the whole reason Clock is
        // injected rather than called statically.
        Clock jumping = new Clock() {
            private int reads;

            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return reads++ == 0 ? start : start.plus(Duration.ofHours(1));
            }
        };
        FakeOllamaClient llm = new FakeOllamaClient()
                .thenAlways(FakeOllamaClient.toolCall("fetch_question", "{}"));
        InterviewSession session = Fixtures.session();
        session.setStartedAt(start);

        agentWith(llm, Fixtures.happyGateway(), jumping)
                .run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState()).isEqualTo(SessionState.FINISHED);
        assertThat(session.getTerminalReason()).isEqualTo(FallbackReason.BUDGET);
        // The budget was checked before spending a generation.
        assertThat(llm.callCount()).isZero();
    }

    // ------------------------------------------------------------------ rung 9

    @Test
    @DisplayName("rung 9: with Ollama down the candidate still completes an interview")
    void anUnreachableModelDegradesRatherThanFailing() {
        FakeToolGateway gateway = new FakeToolGateway()
                .register(ToolName.FETCH_QUESTION, Fixtures.QUESTION_RESULT);
        InterviewSession session = Fixtures.session();

        agentWith(FakeOllamaClient.down(), gateway).run(session, NoOpAgentEvents.INSTANCE);

        // Not FAILED. The fixed pipeline was not deleted, it was demoted to this path.
        assertThat(session.getState()).isEqualTo(SessionState.DEGRADED);
        assertThat(session.getTerminalReason()).isEqualTo(FallbackReason.MODEL_UNREACHABLE);
        assertThat(gateway.invocationsOf(ToolName.FETCH_QUESTION)).isGreaterThan(0);
        assertThat(session.getClosingMessage()).isNotBlank();
        assertThat(session.getTurns()).isNotEmpty();
    }
}
