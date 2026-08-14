package org.interviewer.entity.grading;

import java.util.List;

/**
 * The grader's output.
 *
 * <p>Produced under constrained decoding at temperature 0 — the schema is passed to Ollama in
 * {@code format}, so the model cannot return prose, cannot omit a dimension, and cannot invent a
 * score outside 1-5. Parsing free text for a grade is the failure mode this avoids.
 */
public record Verdict(int overall,
                      String recommendation,
                      List<DimensionScore> dimensions,
                      String summary) {

    public static final List<String> DIMENSIONS = List.of(
            "correctness", "depth", "communication", "practical_experience");
}
