package org.interviewer.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.entity.InterviewSessionPO;
import org.interviewer.entity.InterviewTurnPO;
import org.interviewer.entity.ToolInvocationPO;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.Turn;
import org.interviewer.mapper.InterviewSessionMapper;
import org.interviewer.mapper.InterviewTurnMapper;
import org.interviewer.mapper.ToolInvocationMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Set;

/**
 * Writes a finished session to MySQL.
 *
 * <p>Runs once at the end rather than on every turn. Per-turn writes would put a database round
 * trip on the interview's latency path for no benefit — Redis already holds the live state, and
 * losing a session to a crash costs one interview, not a corpus.
 */
@Slf4j
@Component
public class SessionPersister {

    /**
     * Tools whose arguments are stored in full.
     *
     * <p>Hash-only storage everywhere would make production incidents undebuggable: you cannot
     * work out why the model asked for something when all you kept is a checksum. These three
     * carry no candidate speech, so storing them costs nothing. The other three do quote the
     * candidate — {@code ask_followup.question} and {@code record_evidence.quote} — and get a hash
     * only, which is still enough to detect a repeated call.
     */
    private static final Set<String> STORE_FULL_ARGS = Set.of(
            ToolName.FETCH_QUESTION.wireName(),
            ToolName.SCORE_RESPONSE.wireName(),
            ToolName.RUN_CODE.wireName());

    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final ToolInvocationMapper toolMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SessionPersister(InterviewSessionMapper sessionMapper,
                            InterviewTurnMapper turnMapper,
                            ToolInvocationMapper toolMapper,
                            ObjectMapper objectMapper,
                            Clock clock) {
        this.sessionMapper = sessionMapper;
        this.turnMapper = turnMapper;
        this.toolMapper = toolMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void persist(InterviewSession session) {
        LocalDateTime now = LocalDateTime.now(clock);

        InterviewSessionPO po = new InterviewSessionPO();
        po.setId(session.getSessionId());
        po.setCandidateId(session.getCandidateId());
        po.setJobId(session.getJobId());
        po.setInterviewerId(session.getInterviewerId());
        po.setState(session.getState() == null ? null : session.getState().name());
        po.setTerminalReason(session.getTerminalReason() == null
                ? null : session.getTerminalReason().name());
        po.setClosingMessage(session.getClosingMessage());
        po.setTurnCount(session.getTurnCount());
        po.setToolCallCount(session.getToolCallCount());
        po.setErrorCount(session.getErrorCount());
        po.setPromptTokens(session.getPromptTokens());
        po.setCompletionTokens(session.getCompletionTokens());
        po.setStartedAt(toLocal(session.getStartedAt()));
        po.setFinishedAt(toLocal(session.getFinishedAt()));
        po.setCreatedTime(now);
        po.setUpdatedTime(now);
        sessionMapper.insert(po);

        for (Turn turn : session.getTurns()) {
            InterviewTurnPO turnPo = new InterviewTurnPO();
            turnPo.setSessionId(session.getSessionId());
            turnPo.setSeq(turn.getSeq());
            turnPo.setKind(turn.getKind() == null ? null : turn.getKind().name());
            turnPo.setQuestionId(turn.getQuestionId());
            turnPo.setText(turn.getText());
            turnPo.setSttConfidence(turn.getSttConfidence());
            turnPo.setStartedAt(toLocal(turn.getStartedAt()));
            turnPo.setEndedAt(toLocal(turn.getEndedAt()));
            turnPo.setCreatedTime(now);
            turnMapper.insert(turnPo);
        }

        int seq = 0;
        for (InterviewSession.ToolRecord record : session.getToolLog()) {
            ToolInvocationPO toolPo = new ToolInvocationPO();
            toolPo.setSessionId(session.getSessionId());
            toolPo.setSeq(seq++);
            toolPo.setToolName(record.toolName());
            toolPo.setArgsJson(STORE_FULL_ARGS.contains(record.toolName())
                    ? record.argsJson() : null);
            toolPo.setArgsHash(sha256(record.argsJson()));
            toolPo.setOutcome(record.outcome());
            toolPo.setFallbackReason(record.fallbackReason());
            toolPo.setDurationMs(record.durationMs());
            toolPo.setCreatedTime(now);
            toolMapper.insert(toolPo);
        }

        log.info("persisted session {}: {} turns, {} tool calls",
                session.getSessionId(), session.getTurns().size(), session.getToolLog().size());
    }

    private LocalDateTime toLocal(java.time.Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Only used to normalise args before hashing, so a key-order difference is not a new hash. */
    String canonical(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return String.valueOf(node);
        }
    }
}
