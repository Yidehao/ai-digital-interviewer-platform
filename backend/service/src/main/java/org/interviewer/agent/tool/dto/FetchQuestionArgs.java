package org.interviewer.agent.tool.dto;

/**
 * Arguments for {@code fetch_question}. Mirrors {@code tools/fetch_question.json}.
 *
 * <p>No field is required: a model that just wants the next question should not have to invent a
 * topic to get one. The compact constructor applies the schema's stated default so the record and
 * the schema agree about what an omitted field means — the validator does not fill defaults in.
 */
public record FetchQuestionArgs(String topic, String difficulty, Boolean excludeServed) {
    public FetchQuestionArgs {
        if (excludeServed == null) {
            excludeServed = Boolean.TRUE;
        }
    }
}
