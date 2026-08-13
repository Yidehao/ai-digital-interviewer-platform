package org.interviewer.agent.tool.impl;

import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.dto.RecordEvidenceArgs;
import org.interviewer.agent.tool.dto.RecordEvidenceResult;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.TurnKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The anti-fabrication guard, and the reason it cannot be a literal string match.
 *
 * <p>Two failure modes pull against each other. Too strict and legitimate calls are rejected —
 * speech-to-text punctuation varies, and models near-quote rather than quote — which burns the
 * error budget on a guard that was supposed to help. Too loose and an invented quote passes, which
 * is worse than having no audit trail at all, because it looks like evidence.
 *
 * <p>These tests pin both edges.
 */
class RecordEvidenceToolTest {

    private final RecordEvidenceTool tool = new RecordEvidenceTool();

    private InterviewSession sessionWithAnswer(String answer) {
        InterviewSession session = new InterviewSession();
        session.setSessionId("s-1");
        session.addTurn(TurnKind.QUESTION, "q-1", "How did you scale it?", Instant.now());
        session.addTurn(TurnKind.ANSWER, "q-1", answer, Instant.now());
        return session;
    }

    private ToolContext ctxFor(InterviewSession session) {
        return new ToolContext() {
            @Override
            public String sessionId() {
                return session.getSessionId();
            }

            @Override
            public InterviewSession session() {
                return session;
            }

            @Override
            public boolean detached() {
                return false;
            }
        };
    }

    @Test
    @DisplayName("accepts an exact quote")
    void exactQuoteIsAccepted() {
        InterviewSession session = sessionWithAnswer(
                "we ended up sharding by tenant id because the hot tenant was half the traffic");

        RecordEvidenceResult result = tool.execute(new RecordEvidenceArgs(
                "practical_experience", "we ended up sharding by tenant id",
                "positive", "q-1"), ctxFor(session));

        assertThat(result.accepted()).isTrue();
        assertThat(result.matchedTurnSeq()).isEqualTo(1);
        assertThat(session.getEvidence()).hasSize(1);
    }

    @Test
    @DisplayName("accepts a quote whose punctuation and casing differ from the transcript")
    void punctuationAndCasingDoNotMatter() {
        // Speech-to-text output varies between runs. A guard that rejects on a comma is a guard
        // that rejects real evidence.
        InterviewSession session = sessionWithAnswer(
                "we ended up sharding by tenant id, because the hot tenant was half the traffic");

        RecordEvidenceResult result = tool.execute(new RecordEvidenceArgs(
                "practical_experience", "We ended up sharding by tenant ID!",
                "positive", "q-1"), ctxFor(session));

        assertThat(result.accepted()).isTrue();
    }

    @Test
    @DisplayName("accepts a near-quote that drops a filler word")
    void nearQuotingIsAccepted() {
        // Models tidy up speech when quoting it. This is the single most common legitimate call
        // that a strict containment check would reject.
        InterviewSession session = sessionWithAnswer(
                "so um we basically ended up sharding by tenant id after the incident");

        RecordEvidenceResult result = tool.execute(new RecordEvidenceArgs(
                "practical_experience", "we ended up sharding by tenant id after the incident",
                "positive", "q-1"), ctxFor(session));

        assertThat(result.accepted()).isTrue();
        assertThat(result.similarity()).isGreaterThanOrEqualTo(0.7);
    }

    @Test
    @DisplayName("rejects a fabricated quote that shares only topic words")
    void fabricatedQuoteIsRejected() {
        InterviewSession session = sessionWithAnswer(
                "we ended up sharding by tenant id because the hot tenant was half the traffic");

        RecordEvidenceResult result = tool.execute(new RecordEvidenceArgs(
                "depth", "I designed a consistent hashing ring with virtual nodes and rebalancing",
                "positive", "q-1"), ctxFor(session));

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("quote_not_found");
        assertThat(result.evidenceId()).isNull();
        assertThat(session.getEvidence()).isEmpty();
    }

    @Test
    @DisplayName("will not quote the interviewer's own question back as candidate evidence")
    void onlyAnswerTurnsAreSearched() {
        InterviewSession session = new InterviewSession();
        session.setSessionId("s-1");
        session.addTurn(TurnKind.QUESTION, "q-1",
                "Tell me how you sharded the database by tenant id", Instant.now());

        RecordEvidenceResult result = tool.execute(new RecordEvidenceArgs(
                "depth", "how you sharded the database by tenant id",
                "positive", "q-1"), ctxFor(session));

        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("no_answer_turns");
    }

    @Test
    @DisplayName("recording the same evidence twice does not duplicate the audit trail")
    void repeatedEvidenceIsIdempotent() {
        InterviewSession session = sessionWithAnswer(
                "we ended up sharding by tenant id because the hot tenant was half the traffic");
        RecordEvidenceArgs args = new RecordEvidenceArgs(
                "practical_experience", "we ended up sharding by tenant id", "positive", "q-1");

        RecordEvidenceResult first = tool.execute(args, ctxFor(session));
        RecordEvidenceResult second = tool.execute(args, ctxFor(session));

        assertThat(second.evidenceId()).isEqualTo(first.evidenceId());
        assertThat(session.getEvidence()).hasSize(1);
    }
}
