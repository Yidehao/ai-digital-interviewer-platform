package org.interviewer.agent.tool.dto;

/**
 * Arguments for {@code run_code}. Mirrors {@code tools/run_code.json}.
 *
 * <p>The schema's description carries a negative precondition — do not call this for a verbal
 * description of an approach — because a positive description alone was not enough: the 3B model
 * chose this tool in six benchmark situations containing no code at all.
 */
public record RunCodeArgs(String language, String source, String stdin, Integer timeoutMs) {
    public RunCodeArgs {
        if (stdin == null) {
            stdin = "";
        }
        if (timeoutMs == null) {
            timeoutMs = 3_000;
        }
    }
}
