package org.interviewer.grader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.entity.agent.Evidence;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.entity.agent.WorkingScore;
import org.interviewer.entity.grading.GradingInput;
import org.interviewer.entity.grading.TranscriptTurn;
import org.interviewer.entity.ollama.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proof that the grader cannot see the interviewer's reasoning.
 *
 * <p>The claim under test is not "we remember to filter agent state out before grading". It is that
 * {@link GradingInput} has nowhere to put it. Those are very different claims: the first depends on
 * every future caller remembering, the second is a property of the type.
 *
 * <p>The reflection test below walks {@code GradingInput}'s transitive field types and fails if any
 * of them comes from the agent packages. It looks pedantic. It is the thing that fires when someone
 * adds {@code InterviewSession session} "just for logging" in eight months — which is exactly how
 * this leak happens in real systems, and why it is a build failure rather than a review convention.
 */
class GradingInputIsolationTest {

    private static final Set<String> FORBIDDEN_PACKAGES = Set.of(
            "org.interviewer.entity.agent",
            "org.interviewer.entity.ollama",
            "org.interviewer.agent");

    @Test
    @DisplayName("no field on GradingInput, transitively, comes from the agent packages")
    void gradingInputCannotCarryAgentState() {
        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        List<String> violations = new ArrayList<>();
        queue.add(GradingInput.class);

        while (!queue.isEmpty()) {
            Class<?> type = queue.poll();
            if (!visited.add(type) || type.isPrimitive() || type.getName().startsWith("java.")) {
                continue;
            }
            for (String forbidden : FORBIDDEN_PACKAGES) {
                if (type.getName().startsWith(forbidden + ".")) {
                    violations.add(type.getName());
                }
            }
            for (Field field : type.getDeclaredFields()) {
                queue.add(field.getType());
                // Generic parameters count: List<Turn> would leak just as effectively as Turn.
                if (field.getGenericType() instanceof ParameterizedType parameterized) {
                    for (Type argument : parameterized.getActualTypeArguments()) {
                        if (argument instanceof Class<?> clazz) {
                            queue.add(clazz);
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("GradingInput can reach agent state - the grader is no longer isolated")
                .isEmpty();
    }

    @Test
    @DisplayName("a serialized GradingInput contains no working scores, evidence, or tool names")
    void nothingLeaksThroughSerialization() throws Exception {
        // The reflection test proves the types cannot carry it. This proves the instance does not,
        // which catches the other way it could go wrong: agent state stringified into a text field.
        InterviewSession session = sessionWithPrivateNotes();

        GradingInput input = new GradingInput(
                session.getSessionId(), "SDE", "Assess technical depth.",
                List.of(new TranscriptTurn(0, TranscriptTurn.ANSWER, "I used Redis.", 12)),
                42, List.of());

        String json = new ObjectMapper().writeValueAsString(input);

        assertThat(json)
                .doesNotContain("SECRET_WORKING_NOTE")
                .doesNotContain("SECRET_EVIDENCE")
                .doesNotContain("PLANNING_CONVERSATION")
                .doesNotContain("score_response")
                .doesNotContain("fetch_question");
    }

    @Test
    @DisplayName("the transcript the grader sees marks no turn as a follow-up")
    void followupsAreNotMarkedAsFollowups() {
        // A grader that knows the interviewer chose to probe somewhere is reading its judgement
        // about the candidate - it learns which answers were found weak without being told a score.
        InterviewSession session = sessionWithPrivateNotes();
        session.addTurn(TurnKind.FOLLOWUP, "q-1", "Why that approach?", Instant.now());

        List<String> kinds = session.getTurns().stream()
                .filter(t -> t.getKind() != TurnKind.CLOSING)
                .map(t -> t.getKind() == TurnKind.ANSWER
                        ? TranscriptTurn.ANSWER : TranscriptTurn.QUESTION)
                .toList();

        assertThat(kinds).doesNotContain("FOLLOWUP");
        assertThat(kinds).containsOnly(TranscriptTurn.QUESTION, TranscriptTurn.ANSWER);
    }

    @Test
    @DisplayName("GradingInput exposes exactly the six agreed fields, and no more")
    void theShapeIsFixed() {
        // A new field is not necessarily a leak, but it is always a decision worth making
        // deliberately rather than discovering later.
        List<String> fields = java.util.Arrays.stream(GradingInput.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(fields).containsExactly(
                "sessionId", "jobName", "rubric", "turns", "totalSeconds", "referenceAnswers");
    }

    private InterviewSession sessionWithPrivateNotes() {
        InterviewSession session = new InterviewSession();
        session.setSessionId("s-iso-1");
        session.getMessages().add(ChatMessage.assistant("PLANNING_CONVERSATION"));
        session.getWorkingScores().put("q-1|depth",
                new WorkingScore("q-1", "depth", 2, "SECRET_WORKING_NOTE", "high"));
        session.getEvidence().add(new Evidence("ev-1", "depth", "SECRET_EVIDENCE",
                "negative", "q-1", 1));
        session.addTurn(TurnKind.QUESTION, "q-1", "How would you cache this?", Instant.now());
        session.addTurn(TurnKind.ANSWER, "q-1", "I used Redis.", Instant.now());
        return session;
    }
}
