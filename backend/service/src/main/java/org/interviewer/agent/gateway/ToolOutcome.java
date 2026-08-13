package org.interviewer.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * What came back from one tool call.
 *
 * @param toolName   the wire name, echoed so the caller need not track it
 * @param result     the result document, not yet validated against its schema
 * @param terminal   true only for {@code finish_interview}
 * @param durationMs wall clock for the dispatch, for the per-tool histogram
 */
public record ToolOutcome(String toolName, JsonNode result, boolean terminal, long durationMs) {
}
