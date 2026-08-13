package org.interviewer.agent.tool.dto;

/**
 * Arguments for {@code finish_interview}. Mirrors {@code tools/finish_interview.json}.
 *
 * <p>{@code reason} accepts both {@code time_exhausted} and {@code budget}. The model invented the
 * former and kept using it after being handed the valid values; the name it invented is the better
 * one, so the schema accepts both rather than spending repair turns correcting it.
 */
public record FinishInterviewArgs(String reason, String closingMessage) {
}
