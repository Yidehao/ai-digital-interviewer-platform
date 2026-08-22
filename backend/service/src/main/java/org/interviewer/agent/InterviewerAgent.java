package org.interviewer.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.gateway.ToolExecutionException;
import org.interviewer.agent.gateway.ToolGateway;
import org.interviewer.agent.gateway.ToolOutcome;
import org.interviewer.agent.schema.ToolSchemas;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.entity.agent.FallbackReason;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.entity.ollama.ChatRequest;
import org.interviewer.entity.ollama.ChatResponse;
import org.interviewer.entity.ollama.ToolCall;
import org.interviewer.entity.ollama.ToolSpec;
import org.interviewer.llm.ModelUnavailableException;
import org.interviewer.llm.OllamaClient;
import org.interviewer.utils.AgentProperties;
import org.interviewer.utils.LlmProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The agent loop.
 *
 * <p>The difference from the fixed pipeline in one sentence: <b>the code no longer decides what
 * happens next — the model decides, by choosing a tool.</b> What the code still decides is what
 * happens when the model chooses badly, chooses nothing, or cannot be reached at all, and that is
 * the fallback ladder below.
 *
 * <p>Two invariants the whole design rests on:
 *
 * <ol>
 *   <li><b>Termination is an explicit action.</b> Only {@code finish_interview} ends the loop
 *       normally. That is what makes "no tool call was emitted" unambiguously mean <em>something
 *       went wrong</em> rather than <em>the model is done</em>, and it is what makes the fallback
 *       ladder meaningful instead of guesswork.</li>
 *   <li><b>Every path terminates.</b> Nine rungs, each with a deterministic non-LLM action, and
 *       budgets checked before any model call so an exhausted budget never spends a generation.
 *       The tests assert a terminal state on every rung — eleven repetitions of one assertion,
 *       which is what the "never stalls" claim actually is.</li>
 * </ol>
 *
 * <p>This class knows nothing about SSE, Tomcat or HTTP. It reports through {@link AgentEvents},
 * so the offline eval harness runs the identical code path with {@link NoOpAgentEvents}.
 */
@Slf4j
@Component
public class InterviewerAgent {

    private final OllamaClient llm;
    private final ToolGateway gateway;
    private final ToolSchemas schemas;
    private final FallbackPlanner fallback;
    private final ConversationWindow window;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final LlmProperties llmProperties;
    private final ExecutorService toolExecutor;
    private final CandidateGate candidateGate;
    private final Clock clock;

    public InterviewerAgent(OllamaClient llm,
                            ToolGateway gateway,
                            ToolSchemas schemas,
                            FallbackPlanner fallback,
                            ConversationWindow window,
                            ObjectMapper objectMapper,
                            AgentProperties agentProperties,
                            LlmProperties llmProperties,
                            ExecutorService toolExecutor,
                            CandidateGate candidateGate,
                            Clock clock) {
        this.llm = llm;
        this.gateway = gateway;
        this.schemas = schemas;
        this.fallback = fallback;
        this.window = window;
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.llmProperties = llmProperties;
        this.toolExecutor = toolExecutor;
        this.candidateGate = candidateGate;
        this.clock = clock;
    }

    /**
     * Drive the interview until it reaches a terminal state. Always returns; never throws for a
     * model or tool failure.
     */
    public InterviewSession run(InterviewSession session, AgentEvents events) {
        if (session.getState() == SessionState.CREATED) {
            session.setState(SessionState.RUNNING);
            session.setStartedAt(clock.instant());
        }
        Instant deadline = session.getStartedAt()
                .plusSeconds(agentProperties.getMaxWallClockMinutes() * 60L);

        while (!session.isTerminal()) {
            FallbackReason exhausted = budgetExceeded(session, deadline);
            if (exhausted != null) {
                return close(session, events, exhausted);
            }

            window.evictIfNeeded(session);

            ChatResponse response;
            try {
                response = llm.chat(buildRequest(session));
            } catch (ModelUnavailableException e) {
                // Rung 9. Not a retry: the fixed pipeline is not deleted, it is demoted to the
                // degraded path, and the candidate still finishes an interview.
                log.warn("session {} degrading to scripted: {}",
                        session.getSessionId(), e.getMessage());
                return degrade(session, events);
            }

            session.setTurnCount(session.getTurnCount() + 1);
            if (response.getPromptEvalCount() != null) {
                session.setPromptTokens(session.getPromptTokens() + response.getPromptEvalCount());
            }
            // The number that is actually about latency. See InterviewSession.promptEvalNanos:
            // the token count does not fall when the prefix cache hits, but this does.
            if (response.getPromptEvalDurationNanos() != null) {
                session.setPromptEvalNanos(
                        session.getPromptEvalNanos() + response.getPromptEvalDurationNanos());
            }
            if (response.getEvalCount() != null) {
                session.setCompletionTokens(
                        session.getCompletionTokens() + response.getEvalCount());
            }

            if (!response.hasToolCalls()) {
                if (handleNoToolCall(session, events, response)) {
                    return session;
                }
                continue;
            }

            session.setConsecutiveNoToolCalls(0);
            if (handleToolCall(session, events, response)) {
                return session;
            }
            // The interview's turn-taking, and the reason it is an interview. Without this the
            // loop calls the model again the instant a tool returns; the model sees a question it
            // has already asked and no answer, and asks another one. A real session over the
            // polling transport produced fourteen questions and zero answers in nine seconds.
            if (!awaitCandidate(session)) {
                return close(session, events, FallbackReason.CANDIDATE_TIMEOUT);
            }
        }
        return session;
    }

    /**
     * Wait for an answer, but only when one is actually owed.
     *
     * <p>Most turns are not questions — the model scores a response, records evidence, runs code.
     * Waiting after those would stall the interview on a turn the candidate was never asked to
     * participate in, so the gate is consulted only when the last transcript turn is something a
     * person is expected to answer.
     *
     * @return false when the candidate stopped answering and the interview should end
     */
    private boolean awaitCandidate(InterviewSession session) {
        List<Turn> turns = session.getTurns();
        if (turns.isEmpty()) {
            return true;
        }
        Turn last = turns.get(turns.size() - 1);
        if (last.getKind() != TurnKind.QUESTION && last.getKind() != TurnKind.FOLLOWUP) {
            return true;
        }
        return candidateGate.awaitAnswer(session, last.getSeq(),
                agentProperties.getAnswerTimeoutMs());
    }

    // ------------------------------------------------------------------ rungs 7 and 8

    private FallbackReason budgetExceeded(InterviewSession session, Instant deadline) {
        if (session.getErrorCount() > agentProperties.getMaxToolErrors()) {
            return FallbackReason.ERROR_BUDGET;
        }
        if (session.getTurnCount() >= agentProperties.getMaxTurns()
                || session.getToolCallCount() >= agentProperties.getMaxToolCalls()
                || clock.instant().isAfter(deadline)) {
            return FallbackReason.BUDGET;
        }
        return null;
    }

    // ------------------------------------------------------------------ rungs 1 and 1b

    /** @return true when the interview ended here */
    private boolean handleNoToolCall(InterviewSession session,
                                     AgentEvents events,
                                     ChatResponse response) {
        session.setConsecutiveNoToolCalls(session.getConsecutiveNoToolCalls() + 1);
        session.setErrorCount(session.getErrorCount() + 1);

        boolean secondTime = session.getConsecutiveNoToolCalls() >= 2;
        FallbackReason reason = secondTime
                ? FallbackReason.NO_TOOL_CALL_REPEATED
                : FallbackReason.NO_TOOL_CALL;
        session.recordFallback(reason);
        events.onFallback(reason, truncate(response.contentOrEmpty()));

        if (!secondTime) {
            // One nudge. Exactly one - a second would be the retry loop this design refuses.
            session.getMessages().add(fallback.noToolCallNudge());
            return false;
        }

        // Deterministic branch: move the interview forward without the model if there is anything
        // left to ask, otherwise close cleanly.
        if (serveNextQuestionOurselves(session, events)) {
            return false;
        }
        close(session, events, FallbackReason.NO_TOOL_CALL_REPEATED);
        return true;
    }

    /**
     * Call {@code fetch_question} ourselves. The interview visibly moves forward with no model in
     * the path, which is the difference between a fallback and an outage.
     *
     * @return true when a question was served
     */
    private boolean serveNextQuestionOurselves(InterviewSession session, AgentEvents events) {
        String name = ToolName.FETCH_QUESTION.wireName();
        if (gateway.describe(name).isEmpty()) {
            return false;
        }
        try {
            ToolOutcome outcome = gateway.invoke(name, objectMapper.createObjectNode(),
                    contextFor(session, events));
            JsonNode exhausted = outcome.result().get("exhausted");
            if (exhausted != null && exhausted.asBoolean()) {
                return false;
            }
            session.setToolCallCount(session.getToolCallCount() + 1);
            session.getMessages().add(fallback.servedQuestionForYou(name));
            return true;
        } catch (RuntimeException e) {
            log.warn("session {} could not serve a question during fallback: {}",
                    session.getSessionId(), e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ rungs 2 to 6

    /** @return true when the interview ended here */
    private boolean handleToolCall(InterviewSession session,
                                   AgentEvents events,
                                   ChatResponse response) {
        List<ToolCall> calls = response.getMessage().getToolCalls();
        ToolCall call = calls.get(0);
        session.getMessages().add(ChatMessage.assistantToolCalls(List.of(call)));

        String name = call.toolName();
        JsonNode args = call.arguments() == null ? objectMapper.createObjectNode()
                : call.arguments();

        // Rung 3 - unknown tool.
        Optional<ToolGateway.ToolDescriptor> descriptor = gateway.describe(name);
        if (descriptor.isEmpty()) {
            recordError(session, events, FallbackReason.UNKNOWN_TOOL, name);
            session.getMessages().add(fallback.unknownTool(name, gateway.availableTools()));
            return false;
        }
        ToolName tool = ToolName.fromWireName(name).orElseThrow();

        // Rung 2 - schema-invalid arguments, one repair.
        List<String> argErrors = schemas.validateArgs(tool, args);
        if (!argErrors.isEmpty()) {
            logTool(session, name, args, "SCHEMA_REJECTED", FallbackReason.INVALID_ARGS, 0L);
            recordError(session, events, FallbackReason.INVALID_ARGS,
                    name + ": " + String.join("; ", argErrors));
            if (session.getRepairCount() < agentProperties.getMaxRepairs()) {
                session.setRepairCount(session.getRepairCount() + 1);
                session.getMessages().add(fallback.invalidArgs(name, argErrors));
                return false;
            }
            // Repairs spent. A model that cannot fix its arguments when handed the validation
            // errors will not fix them on the third attempt either - it re-emits the same value.
            // Demote to rung 1's deterministic branch rather than repairing again.
            session.setRepairCount(0);
            if (serveNextQuestionOurselves(session, events)) {
                return false;
            }
            close(session, events, FallbackReason.INVALID_ARGS);
            return true;
        }

        // Rung 6 - the identical call twice running.
        String hash = hashOf(name, args);
        if (hash.equals(session.getLastToolCallHash())) {
            recordError(session, events, FallbackReason.REPEATED_CALL, name);
            session.getMessages().add(fallback.repeatedCall(name));
            session.setLastToolCallHash(null);
            return false;
        }
        session.setLastToolCallHash(hash);

        // Rung 4 - dispatch, with the timeout enforced here rather than by the tool.
        events.onToolStart(name);
        ToolOutcome outcome;
        long started = System.nanoTime();
        try {
            outcome = dispatchWithTimeout(session, events, name, args, descriptor.get().timeoutMs());
        } catch (ToolExecutionException e) {
            long ms = (System.nanoTime() - started) / 1_000_000L;
            events.onToolEnd(name, ms, false);
            logTool(session, name, args,
                    "timeout".equals(e.code()) ? "TIMEOUT" : "ERROR",
                    FallbackReason.TOOL_ERROR, ms);
            recordError(session, events, FallbackReason.TOOL_ERROR, e.code() + ": " + e.getMessage());
            session.getMessages().add(fallback.toolError(name, e.code(), e.getMessage()));
            return false;
        }
        events.onToolEnd(name, outcome.durationMs(), true);
        session.setToolCallCount(session.getToolCallCount() + 1);
        session.setRepairCount(0);
        logTool(session, name, args, "OK", null, outcome.durationMs());

        // Rung 5 - our own result failed our own schema. Our bug.
        List<String> resultErrors = schemas.validateResult(tool, outcome.result());
        if (!resultErrors.isEmpty()) {
            log.error("session {} produced an invalid {} result: {}",
                    session.getSessionId(), name, resultErrors);
            recordError(session, events, FallbackReason.RESULT_SCHEMA_INVALID,
                    name + ": " + String.join("; ", resultErrors));
            session.getMessages().add(fallback.internalError(name));
            return false;
        }

        session.getMessages().add(ChatMessage.toolResult(name, outcome.result().toString()));

        if (descriptor.get().terminal()) {
            session.setState(SessionState.FINISHED);
            session.setFinishedAt(clock.instant());
            String closing = textOf(outcome.result(), "closingMessage");
            session.setClosingMessage(closing);
            events.onFinished(SessionState.FINISHED, closing);
            return true;
        }
        return false;
    }

    private ToolOutcome dispatchWithTimeout(InterviewSession session,
                                            AgentEvents events,
                                            String name,
                                            JsonNode args,
                                            long timeoutMs) {
        ToolContext ctx = contextFor(session, events);
        Future<ToolOutcome> future = toolExecutor.submit(() -> gateway.invoke(name, args, ctx));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ToolExecutionException("timeout",
                    name + " exceeded its " + timeoutMs + " ms budget", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("interrupted", name + " was interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ToolExecutionException tee) {
                throw tee;
            }
            throw new ToolExecutionException("tool_threw",
                    name + " threw " + (cause == null ? "" : cause.getMessage()), cause);
        }
    }

    // ------------------------------------------------------------------ rung 9 and closing

    /**
     * Rung 9 — the model is unreachable, so run the interview without one.
     *
     * <p>Worth being able to recite: <b>the fixed pipeline was not deleted, it was demoted to the
     * degraded path.</b> That is both the honest answer to "what happens when the model is down"
     * and the reason this migration is low risk.
     */
    private InterviewSession degrade(InterviewSession session, AgentEvents events) {
        session.recordFallback(FallbackReason.MODEL_UNREACHABLE);
        events.onFallback(FallbackReason.MODEL_UNREACHABLE, "serving remaining questions unaided");

        while (serveNextQuestionOurselves(session, events)) {
            if (session.getToolCallCount() >= agentProperties.getMaxToolCalls()) {
                break;
            }
            // The scripted fallback has to take turns as well. A candidate whose model went down
            // mid-interview should get a slower interview, not a wall of questions.
            if (!awaitCandidate(session)) {
                break;
            }
        }

        session.setState(SessionState.DEGRADED);
        session.setFinishedAt(clock.instant());
        session.setTerminalReason(FallbackReason.MODEL_UNREACHABLE);
        String closing = fallback.closingMessageFor(FallbackReason.MODEL_UNREACHABLE);
        session.setClosingMessage(closing);
        session.addTurn(TurnKind.CLOSING, null, closing, clock.instant());
        events.onFinished(SessionState.DEGRADED, closing);
        return session;
    }

    /** End the interview deterministically, without needing the model to cooperate. */
    private InterviewSession close(InterviewSession session,
                                   AgentEvents events,
                                   FallbackReason reason) {
        session.recordFallback(reason);
        events.onFallback(reason, "closing the interview");

        String closing = fallback.closingMessageFor(reason);
        session.setTerminalReason(reason);
        session.setClosingMessage(closing);
        session.setState(SessionState.FINISHED);
        session.setFinishedAt(clock.instant());
        session.addTurn(TurnKind.CLOSING, null, closing, clock.instant());
        events.onFinished(SessionState.FINISHED, closing);
        return session;
    }

    // ------------------------------------------------------------------ plumbing

    private ChatRequest buildRequest(InterviewSession session) {
        return ChatRequest.builder()
                .model(llmProperties.getModel())
                .messages(window.assemble(session))
                .tools(toolSpecs())
                .stream(false)
                .keepAlive(llmProperties.getKeepAlive())
                .options(ChatRequest.options(
                        llmProperties.getTemperature(), llmProperties.getNumCtx()))
                .build();
    }

    /**
     * The schemas the model sees, with the annotation-only keywords stripped.
     *
     * <p>{@code $schema}, {@code $id} and {@code title} carry no information the model can act on,
     * and the benchmark that measured 100% argument validity did not have them in front of it.
     * Sending what was measured is the point.
     */
    private List<ToolSpec> toolSpecs() {
        List<ToolSpec> specs = new ArrayList<>();
        for (String name : gateway.availableTools()) {
            ToolName tool = ToolName.fromWireName(name).orElse(null);
            if (tool == null) {
                continue;
            }
            JsonNode document = schemas.argsDocument(tool);
            String description = textOf(document, "description");
            if (document.isObject()) {
                com.fasterxml.jackson.databind.node.ObjectNode parameters =
                        (com.fasterxml.jackson.databind.node.ObjectNode) document;
                parameters.remove(List.of("$schema", "$id", "title", "description"));
                specs.add(ToolSpec.of(name, description, parameters));
            }
        }
        return specs;
    }

    private void logTool(InterviewSession session,
                         String name,
                         JsonNode args,
                         String outcome,
                         FallbackReason reason,
                         long durationMs) {
        session.getToolLog().add(new InterviewSession.ToolRecord(
                name, canonical(args), outcome, reason == null ? null : reason.name(), durationMs));
    }

    private void recordError(InterviewSession session,
                             AgentEvents events,
                             FallbackReason reason,
                             String detail) {
        session.setErrorCount(session.getErrorCount() + 1);
        session.recordFallback(reason);
        events.onFallback(reason, truncate(detail));
    }

    private ToolContext contextFor(InterviewSession session, AgentEvents events) {
        return new SessionToolContext(session, events);
    }

    private String hashOf(String name, JsonNode args) {
        return Integer.toHexString((name + "|" + canonical(args)).hashCode());
    }

    /**
     * Key ordering has to be stable or two identical calls hash differently and rung 6 never
     * fires. Jackson preserves insertion order, which is the model's ordering, not ours.
     */
    private String canonical(JsonNode args) {
        try {
            return objectMapper.writer()
                    .with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(args);
        } catch (Exception e) {
            return String.valueOf(args);
        }
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "...";
    }

    /** The session, exposed to tools through the narrow contract they are allowed to see. */
    private record SessionToolContext(InterviewSession session, AgentEvents events)
            implements ToolContext {
        @Override
        public String sessionId() {
            return session.getSessionId();
        }

        @Override
        public InterviewSession session() {
            return session;
        }

        @Override
        public AgentEvents events() {
            return events;
        }

        @Override
        public boolean detached() {
            return false;
        }
    }
}
