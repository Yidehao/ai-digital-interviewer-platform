package org.interviewer.agent.tool.impl;

import org.interviewer.agent.tool.InterviewTool;
import org.interviewer.agent.tool.ToolContext;
import org.interviewer.agent.tool.ToolName;
import org.interviewer.agent.tool.dto.RecordEvidenceArgs;
import org.interviewer.agent.tool.dto.RecordEvidenceResult;
import org.interviewer.entity.agent.Evidence;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.TurnKind;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Attach a quote from the candidate to a competency, building an auditable trail.
 *
 * <p>The guard here is that the quote must actually appear in something the candidate said. A model
 * asked to justify a judgement will otherwise produce a plausible-sounding quote that was never
 * uttered, and an audit trail of invented quotes is worse than no audit trail — it looks like
 * evidence.
 *
 * <p><b>But the check must be lenient, and that is the subtle part.</b> Strict containment
 * over-rejects badly: speech-to-text punctuation and casing vary run to run, and models near-quote
 * rather than quote — they drop a filler word, fix a false start, tidy an "um". Written strictly,
 * the check rejects legitimate calls and burns the error budget, turning a useful guard into a
 * source of spurious failures. So both sides are lowercased, stripped of punctuation and collapsed
 * to single spaces, and compared on a token-overlap threshold rather than literally.
 *
 * <p>Only {@code ANSWER} turns are searched. Quoting the interviewer's own question back as
 * evidence about the candidate would be self-referential, and the transcript is what makes that
 * distinction possible.
 */
@Component
public class RecordEvidenceTool
        implements InterviewTool<RecordEvidenceArgs, RecordEvidenceResult> {

    /**
     * Fraction of the quote's words that must appear in one answer turn.
     *
     * <p>0.7 rather than 1.0 deliberately: a model that drops one word in five from a twenty-word
     * quote is quoting, not fabricating. A fabricated quote shares topic words with the answer but
     * not seven in ten of them in the same turn.
     */
    private static final double SIMILARITY_THRESHOLD = 0.7;

    @Override
    public ToolName name() {
        return ToolName.RECORD_EVIDENCE;
    }

    @Override
    public Class<RecordEvidenceArgs> argsType() {
        return RecordEvidenceArgs.class;
    }

    @Override
    public Class<RecordEvidenceResult> resultType() {
        return RecordEvidenceResult.class;
    }

    @Override
    public RecordEvidenceResult execute(RecordEvidenceArgs args, ToolContext ctx) {
        InterviewSession session = ctx.session();

        List<Turn> answers = session.getTurns().stream()
                .filter(t -> t.getKind() == TurnKind.ANSWER)
                .toList();

        if (answers.isEmpty()) {
            return new RecordEvidenceResult(false, null, args.competency(), null, null,
                    "no_answer_turns");
        }

        List<String> quoteWords = words(args.quote());
        if (quoteWords.isEmpty()) {
            return new RecordEvidenceResult(false, null, args.competency(), null, 0.0,
                    "quote_not_found");
        }

        Turn best = null;
        double bestScore = 0.0;
        for (Turn answer : answers) {
            double score = overlap(quoteWords, words(answer.getText()));
            if (score > bestScore) {
                bestScore = score;
                best = answer;
            }
        }

        if (best == null || bestScore < SIMILARITY_THRESHOLD) {
            return new RecordEvidenceResult(false, null, args.competency(), null, bestScore,
                    "quote_not_found");
        }

        // Stable id for a repeated (competency, quote): recording the same evidence twice should
        // be a no-op rather than a duplicate row in the audit trail.
        String evidenceId = "ev-" + Integer.toHexString(
                (args.competency() + "|" + normalise(args.quote())).hashCode());

        boolean known = session.getEvidence().stream()
                .anyMatch(e -> evidenceId.equals(e.getEvidenceId()));
        if (!known) {
            session.getEvidence().add(new Evidence(evidenceId, args.competency(), args.quote(),
                    args.judgment(), args.questionId(), best.getSeq()));
        }

        return new RecordEvidenceResult(true, evidenceId, args.competency(), best.getSeq(),
                bestScore, null);
    }

    /** Fraction of the quote's words present in the answer. Word-level, so order does not matter. */
    private double overlap(List<String> quoteWords, List<String> answerWords) {
        if (quoteWords.isEmpty()) {
            return 0.0;
        }
        long matched = quoteWords.stream().filter(answerWords::contains).count();
        return (double) matched / quoteWords.size();
    }

    private List<String> words(String text) {
        String normalised = normalise(text);
        return normalised.isEmpty() ? List.of() : List.of(normalised.split(" "));
    }

    /** Lowercase, drop punctuation, collapse whitespace. Applied to both sides before comparing. */
    private String normalise(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
