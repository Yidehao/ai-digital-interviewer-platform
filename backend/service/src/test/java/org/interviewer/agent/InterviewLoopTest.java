package org.interviewer.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.agent.schema.ToolSchemas;
import org.interviewer.agent.support.FakeOllamaClient;
import org.interviewer.agent.support.FakeToolGateway;
import org.interviewer.agent.support.Fixtures;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.llm.OllamaClient;
import org.interviewer.utils.AgentProperties;
import org.interviewer.utils.LlmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The loop end to end: a whole interview against a stubbed model, and the properties that have to
 * hold no matter what the model does.
 */
class InterviewLoopTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentProperties agentProperties = new AgentProperties();
    private final LlmProperties llmProperties = new LlmProperties();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private InterviewerAgent agentWith(OllamaClient llm, FakeToolGateway gateway) {
        return new InterviewerAgent(llm, gateway, ToolSchemas.compiled(mapper),
                new FallbackPlanner(), new ConversationWindow(agentProperties), mapper,
                agentProperties, llmProperties, executor,
                CandidateGate.NONE, Clock.systemUTC());
    }

    // ------------------------------------------------------------------ the Phase 2 demo

    @Test
    @DisplayName("drives a complete interview against a stubbed model and produces a transcript")
    void drivesACompleteInterview() {
        InterviewSession session = Fixtures.session();

        // Tools that mutate the transcript the way the real ones will in Phase 3. Phase 2's tools
        // are fakes, but the transcript they build is the real structure the grader will read.
        FakeToolGateway gateway = new FakeToolGateway()
                .register(ToolName.FETCH_QUESTION, false, 3_000L, args -> {
                    session.addTurn(TurnKind.QUESTION, "q-1",
                            "How would you cache a read-heavy endpoint?", Instant.now());
                    return FakeToolGateway.read(Fixtures.QUESTION_RESULT);
                })
                .register(ToolName.ASK_FOLLOWUP, false, 3_000L, args -> {
                    session.addTurn(TurnKind.FOLLOWUP, "q-1",
                            args.get("question").asText(), Instant.now());
                    return FakeToolGateway.read(Fixtures.FOLLOWUP_RESULT);
                })
                .register(ToolName.SCORE_RESPONSE, Fixtures.SCORE_RESULT)
                .register(ToolName.RECORD_EVIDENCE, """
                        {"accepted":true,"evidenceId":"ev-1","competency":"depth",
                         "matchedTurnSeq":1,"similarity":0.91,"reason":null}""")
                .register(ToolName.FINISH_INTERVIEW, true, 3_000L, args -> {
                    session.addTurn(TurnKind.CLOSING, null,
                            args.get("closingMessage").asText(), Instant.now());
                    return FakeToolGateway.read(Fixtures.FINISH_RESULT);
                });

        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.toolCall("fetch_question", """
                        {"difficulty":"medium"}"""))
                .then(FakeOllamaClient.toolCall("score_response", """
                        {"questionId":"q-1","dimension":"depth","score":3,
                         "evidence":"Named invalidation but not a strategy."}"""))
                .then(FakeOllamaClient.toolCall("ask_followup", """
                        {"question":"What invalidation strategy did you use?",
                         "parentQuestionId":"q-1","competency":"depth"}"""))
                .then(FakeOllamaClient.toolCall("record_evidence", """
                        {"competency":"depth","quote":"we sharded by tenant id",
                         "judgment":"positive","questionId":"q-1"}"""))
                .then(FakeOllamaClient.toolCall("finish_interview", """
                        {"reason":"complete",
                         "closingMessage":"That covers it. Thanks for your time."}"""));

        // The candidate answers between the model's turns, as they would in production.
        session.getMessages().add(ChatMessage.user(
                new SystemPromptBuilder().wrapAnswer("I'd put Redis in front of it.")));

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(session.getState()).isEqualTo(SessionState.FINISHED);
        assertThat(session.getTerminalReason())
                .as("the model ended this itself, so no fallback reason should be set")
                .isNull();
        assertThat(session.getFallbackCounts()).isEmpty();
        assertThat(gateway.invocations).containsExactly(
                "fetch_question", "score_response", "ask_followup",
                "record_evidence", "finish_interview");

        printTranscript(session);

        assertThat(session.getTurns()).extracting(Turn::getKind)
                .containsExactly(TurnKind.QUESTION, TurnKind.FOLLOWUP, TurnKind.CLOSING);
    }

    // ------------------------------------------------------------------ grader isolation

    @Test
    @DisplayName("the transcript contains nothing from the model conversation")
    void theTranscriptAndTheModelConversationStaySeparate() {
        InterviewSession session = Fixtures.session();
        FakeToolGateway gateway = new FakeToolGateway()
                .register(ToolName.SCORE_RESPONSE, Fixtures.SCORE_RESULT)
                .register(ToolName.FETCH_QUESTION, false, 3_000L, args -> {
                    session.addTurn(TurnKind.QUESTION, "q-1", "Tell me about caching.",
                            Instant.now());
                    return FakeToolGateway.read(Fixtures.QUESTION_RESULT);
                })
                .register(ToolName.FINISH_INTERVIEW, true, 3_000L,
                        args -> FakeToolGateway.read(Fixtures.FINISH_RESULT));

        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.toolCall("fetch_question", "{}"))
                .then(FakeOllamaClient.toolCall("score_response", """
                        {"questionId":"q-1","dimension":"depth","score":2,
                         "evidence":"SECRET_WORKING_NOTE"}"""))
                .then(FakeOllamaClient.toolCall("finish_interview", """
                        {"reason":"complete","closingMessage":"Thanks."}"""));

        agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);

        // The working note the interviewer wrote to itself is in the model conversation...
        assertThat(session.getMessages().toString()).contains("SECRET_WORKING_NOTE");

        // ...and nowhere in what the grader will be handed. This is not a filter that could be
        // got wrong later - the grader is given a List<Turn>, and Turn has nowhere to put it.
        assertThat(session.transcript().toString())
                .doesNotContain("SECRET_WORKING_NOTE")
                .doesNotContain("score_response")
                .doesNotContain("tool_call");
    }

    // ------------------------------------------------------------------ termination as a property

    @Test
    @DisplayName("terminates within budget across 200 randomly misbehaving models")
    void alwaysTerminates() {
        Random random = new Random(20260813L);

        for (int run = 0; run < 200; run++) {
            FakeOllamaClient llm = new FakeOllamaClient();
            for (int turn = 0; turn < 30; turn++) {
                llm.then(randomBehaviour(random));
            }
            llm.thenAlways(randomBehaviour(random));

            FakeToolGateway gateway = Fixtures.happyGateway();
            InterviewSession session = Fixtures.session();

            long started = System.currentTimeMillis();
            agentWith(llm, gateway).run(session, NoOpAgentEvents.INSTANCE);
            long elapsed = System.currentTimeMillis() - started;

            assertThat(session.getState().isTerminal())
                    .as("run %d ended in %s", run, session.getState())
                    .isTrue();
            assertThat(session.getTurnCount())
                    .as("run %d used %d turns", run, session.getTurnCount())
                    .isLessThanOrEqualTo(agentProperties.getMaxTurns() + 1);
            assertThat(elapsed).as("run %d took %d ms", run, elapsed).isLessThan(5_000L);
        }
    }

    private Supplier<org.interviewer.entity.ollama.ChatResponse> randomBehaviour(Random random) {
        return switch (random.nextInt(7)) {
            case 0 -> FakeOllamaClient.prose("Let me think about that.");
            case 1 -> FakeOllamaClient.toolCall("fetch_question",
                    "{\"topic\":\"t" + random.nextInt(1000) + "\"}");
            // Invalid: score out of range.
            case 2 -> FakeOllamaClient.toolCall("score_response",
                    """
                    {"questionId":"q-1","dimension":"depth","score":99,"evidence":"x"}""");
            case 3 -> FakeOllamaClient.toolCall("invented_tool_" + random.nextInt(5), "{}");
            // Invalid: hallucinated field, rejected by additionalProperties:false.
            case 4 -> FakeOllamaClient.toolCall("fetch_question",
                    """
                    {"difficulty_level":"medium"}""");
            case 5 -> FakeOllamaClient.toolCall("ask_followup",
                    "{\"question\":\"Why?\",\"parentQuestionId\":\"q-" + random.nextInt(5) + "\"}");
            default -> FakeOllamaClient.toolCall("finish_interview",
                    """
                    {"reason":"complete","closingMessage":"Thanks for your time."}""");
        };
    }

    // ------------------------------------------------------------------ prompt shape

    @Test
    @DisplayName("the system prompt leads every request and is byte-identical across turns")
    void theImmutablePrefixNeverChanges() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.toolCall("fetch_question", "{}"))
                .then(FakeOllamaClient.toolCall("score_response", """
                        {"questionId":"q-1","dimension":"depth","score":3,"evidence":"ok"}"""))
                .then(FakeOllamaClient.toolCall("finish_interview", """
                        {"reason":"complete","closingMessage":"Thanks."}"""));
        InterviewSession session = Fixtures.session();

        agentWith(llm, Fixtures.happyGateway()).run(session, NoOpAgentEvents.INSTANCE);

        assertThat(llm.requests).hasSizeGreaterThan(1);
        List<String> firstMessages = llm.requests.stream()
                .map(r -> r.getMessages().get(0).getContent())
                .distinct()
                .toList();

        // One distinct value means every turn reused the identical prefix. Any variation here -
        // even whitespace - would bust Ollama's KV cache and silently cost a full prompt
        // re-evaluation, measured at ~20 s on a 1.5k-token prompt.
        assertThat(firstMessages).hasSize(1);
        assertThat(llm.requests).allSatisfy(r ->
                assertThat(r.getMessages().get(0).getRole()).isEqualTo("system"));
    }

    @Test
    @DisplayName("tool schemas go to the model without the annotation-only keywords")
    void toolSpecsCarryTheParametersAndNotTheBookkeeping() {
        FakeOllamaClient llm = new FakeOllamaClient()
                .then(FakeOllamaClient.toolCall("finish_interview", """
                        {"reason":"complete","closingMessage":"Thanks."}"""));

        agentWith(llm, Fixtures.happyGateway())
                .run(Fixtures.session(), NoOpAgentEvents.INSTANCE);

        var spec = llm.requests.get(0).getTools().stream()
                .filter(t -> t.getFunction().getName().equals("fetch_question"))
                .findFirst()
                .orElseThrow();

        assertThat(spec.getFunction().getDescription()).contains("question bank");
        var parameters = spec.getFunction().getParameters();
        assertThat(parameters.has("properties")).isTrue();
        assertThat(parameters.get("additionalProperties").asBoolean()).isFalse();
        // These carry nothing the model can act on, and the 100%-validity benchmark did not have
        // them in front of it. Send what was measured.
        assertThat(parameters.has("$schema")).isFalse();
        assertThat(parameters.has("$id")).isFalse();
        assertThat(parameters.has("title")).isFalse();
    }

    private void printTranscript(InterviewSession session) {
        System.out.println("\n--- transcript for " + session.getSessionId() + " ---");
        for (Turn turn : session.transcript()) {
            System.out.printf("  [%d] %-8s %s%n", turn.getSeq(), turn.getKind(), turn.getText());
        }
        System.out.println("  state=" + session.getState()
                + " turns=" + session.getTurnCount()
                + " toolCalls=" + session.getToolCallCount()
                + " errors=" + session.getErrorCount());
        System.out.println("--- end ---\n");
    }
}
