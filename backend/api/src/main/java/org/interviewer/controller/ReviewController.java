package org.interviewer.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.entity.InterviewVerdictPO;
import org.interviewer.entity.VerdictReviewPO;
import org.interviewer.entity.bo.ReviewDecisionBO;
import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.grace.result.ResponseStatusEnum;
import org.interviewer.mapper.InterviewTurnMapper;
import org.interviewer.mapper.InterviewVerdictMapper;
import org.interviewer.mapper.VerdictReviewMapper;
import org.interviewer.entity.InterviewTurnPO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The review console's API — and the reason the interview path is usable at all.
 *
 * <p>Before this, an agent interview produced a structured verdict with per-dimension evidence,
 * wrote it to {@code interview_verdict}, and <b>no human being could see it</b>. The admin panel's
 * "Interview Records" screen reads the scripted pipeline's HTML blob and knows nothing about any of
 * this. A tool whose output nobody can read is not a screening aid; it is a log file.
 *
 * <p><b>The endpoint split is the anti-automation-bias design, and it is the point of this class.</b>
 * {@link #transcript} returns the interview with no scores in it. {@link #verdict} returns the
 * model's assessment. They are separate calls so the console can show a reviewer the candidate's
 * actual answers, take the reviewer's own scores, and only then reveal what the model said.
 *
 * <p>Merging them into one convenient endpoint would quietly undo that: any client would render
 * both together, the reviewer would read "overall: 2" before the first answer, and the human
 * judgement this whole design depends on would become an endorsement of the machine's. The
 * documented failure of decision-support tools is not that people ignore them — it is that they
 * agree with them, particularly when agreeing is one click.
 */
@Slf4j
@RestController
@RequestMapping("review")
public class ReviewController {

    private final InterviewVerdictMapper verdictMapper;
    private final InterviewTurnMapper turnMapper;
    private final VerdictReviewMapper reviewMapper;
    private final ObjectMapper objectMapper;

    public ReviewController(InterviewVerdictMapper verdictMapper,
                            InterviewTurnMapper turnMapper,
                            VerdictReviewMapper reviewMapper,
                            ObjectMapper objectMapper) {
        this.verdictMapper = verdictMapper;
        this.turnMapper = turnMapper;
        this.reviewMapper = reviewMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * The review queue.
     *
     * <p>Carries no scores. A queue sorted by machine score has made the decision before anyone
     * opens a row, so it carries what is needed to triage instead: when, whether repeated grading
     * disagreed with itself, and whether anyone has looked yet.
     */
    @GetMapping("queue")
    public GraceJSONResult queue(@RequestParam(defaultValue = "false") boolean unreviewedOnly) {
        QueryWrapper<InterviewVerdictPO> where = new QueryWrapper<>();
        if (unreviewedOnly) {
            where.isNull("reviewed_by");
        }
        where.orderByDesc("created_time");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (InterviewVerdictPO po : verdictMapper.selectList(where)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionId", po.getSessionId());
            row.put("candidateId", po.getCandidateId());
            row.put("jobId", po.getJobId());
            row.put("needsHumanReview", po.getNeedsHumanReview());
            row.put("dimensionSpread", po.getDimensionSpread());
            row.put("samples", po.getSamples());
            row.put("reviewedBy", po.getReviewedBy());
            row.put("createdTime", po.getCreatedTime());
            // Deliberately absent: overall, recommendation, dimensions.
            rows.add(row);
        }
        return GraceJSONResult.ok(rows);
    }

    /**
     * The interview itself, with no assessment attached.
     *
     * <p>This is what a reviewer reads first. It is a separate endpoint from {@link #verdict} so
     * that reading the candidate's answers is possible without seeing the machine's opinion of them.
     */
    @GetMapping("{sessionId}/transcript")
    public GraceJSONResult transcript(@PathVariable String sessionId) {
        QueryWrapper<InterviewTurnPO> where = new QueryWrapper<>();
        where.eq("session_id", sessionId).orderByAsc("seq");

        List<Map<String, Object>> turns = new ArrayList<>();
        for (InterviewTurnPO turn : turnMapper.selectList(where)) {
            Map<String, Object> row = new LinkedHashMap<>();
            // FOLLOWUP is flattened to QUESTION here for the same reason the grader never sees it:
            // "the interviewer chose to probe here" is a judgement about the candidate, and a
            // reviewer forming an independent view should not be handed it either.
            row.put("kind", "ANSWER".equals(String.valueOf(turn.getKind())) ? "ANSWER" : "QUESTION");
            row.put("text", turn.getText());
            row.put("sttConfidence", turn.getSttConfidence());
            turns.add(row);
        }
        return GraceJSONResult.ok(turns);
    }

    /**
     * The model's assessment. Fetched only after the reviewer has committed their own.
     *
     * <p>Includes the claims it extracted, the evidence behind each score, how many times it was
     * sampled and how far the samples disagreed, and the provenance needed to reconstruct it. A
     * reviewer disagreeing with a score should be able to see exactly what the model was looking at.
     */
    @GetMapping("{sessionId}/verdict")
    public GraceJSONResult verdict(@PathVariable String sessionId) {
        InterviewVerdictPO po = verdictMapper.selectById(sessionId);
        if (po == null) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.SYSTEM_OPERATION_ERROR);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", po.getSessionId());
        out.put("overall", po.getOverall());
        out.put("recommendation", po.getRecommendation());
        out.put("summary", po.getSummary());
        out.put("dimensions", readJson(po.getDimensionsJson()));
        out.put("claims", readJson(po.getClaimsJson()));
        out.put("samples", po.getSamples());
        out.put("dimensionSpread", po.getDimensionSpread());
        out.put("overallSpread", po.getOverallSpread());
        out.put("needsHumanReview", po.getNeedsHumanReview());
        out.put("reviewReason", po.getReviewReason());
        // Always true. Surfaced so the UI states it rather than the reviewer inferring it.
        out.put("advisory", po.getAdvisory());
        out.put("model", po.getModel());
        out.put("rubricHash", po.getRubricHash());
        out.put("schemaVersion", po.getSchemaVersion());
        out.put("gradedMs", po.getGradedMs());
        return GraceJSONResult.ok(out);
    }

    /** Any assessments already recorded by people, so a second reviewer can see there was a first. */
    @GetMapping("{sessionId}/reviews")
    public GraceJSONResult reviews(@PathVariable String sessionId) {
        QueryWrapper<VerdictReviewPO> where = new QueryWrapper<>();
        where.eq("session_id", sessionId);
        return GraceJSONResult.ok(reviewMapper.selectList(where));
    }

    /**
     * Record a human's assessment and decision.
     *
     * <p>The decision is the reviewer's. There is no endpoint that lets the model's verdict become
     * an outcome, and no column for one.
     */
    @PostMapping("decide")
    public GraceJSONResult decide(@RequestBody ReviewDecisionBO decision) {
        if (decision.getSessionId() == null || decision.getReviewedBy() == null
                || decision.getDecision() == null) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.SYSTEM_OPERATION_ERROR);
        }

        VerdictReviewPO review = new VerdictReviewPO();
        review.setSessionId(decision.getSessionId());
        review.setReviewedBy(decision.getReviewedBy());
        review.setOverall(decision.getOverall());
        review.setCorrectness(decision.getCorrectness());
        review.setDepth(decision.getDepth());
        review.setCommunication(decision.getCommunication());
        review.setPracticalExperience(decision.getPracticalExperience());
        review.setDecision(decision.getDecision());
        review.setNotes(decision.getNotes());
        review.setCreatedTime(LocalDateTime.now());

        QueryWrapper<VerdictReviewPO> existing = new QueryWrapper<>();
        existing.eq("session_id", decision.getSessionId())
                .eq("reviewed_by", decision.getReviewedBy());
        if (reviewMapper.selectCount(existing) > 0) {
            reviewMapper.update(review, existing);
        } else {
            reviewMapper.insert(review);
        }

        // Mark the verdict reviewed, but only by the first reviewer to sign off - a second reviewer
        // adds a row rather than replacing who is on record.
        InterviewVerdictPO verdict = verdictMapper.selectById(decision.getSessionId());
        if (verdict != null && verdict.getReviewedBy() == null) {
            verdict.setReviewedBy(decision.getReviewedBy());
            verdict.setReviewedTime(LocalDateTime.now());
            verdictMapper.updateById(verdict);
        }
        log.info("session {} reviewed by {}: {}", decision.getSessionId(),
                decision.getReviewedBy(), decision.getDecision());
        return GraceJSONResult.ok();
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("could not parse stored JSON: {}", e.getMessage());
            return List.of();
        }
    }
}
