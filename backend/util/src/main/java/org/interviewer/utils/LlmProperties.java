package org.interviewer.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * How to talk to Ollama for the agent path.
 *
 * <p>A parallel tree to the legacy {@code ollama.*} config rather than a replacement: the old
 * {@code OllamaTask} binds to that one, and rung 9 of the fallback ladder still needs the fixed
 * pipeline to work. The strangler migration only holds if the thing being strangled keeps running.
 */
@Data
@Component
@ConfigurationProperties(prefix = "interviewer.llm")
public class LlmProperties {

    private String baseUrl = "http://127.0.0.1:11434";

    /**
     * Chosen by measurement in Phase 0.5, not by preference: 100% tool emission and 100% argument
     * validity on the v2 schemas, and it resisted both prompt injections that llama3.2:3b obeyed.
     */
    private String model = "qwen2.5:7b-instruct";

    /** Interviewer temperature. The grader runs at 0 with constrained decoding. */
    private double temperature = 0.4;

    /**
     * Context window. Large enough that the sliding window, not truncation, is what bounds the
     * prompt - silent truncation would drop the tool schemas off the front and look like the model
     * forgetting how to call tools.
     */
    private Integer numCtx = 8192;

    /**
     * Keeps the model resident between turns. An evict-and-reload lands squarely on the path we
     * quote latency for.
     */
    private String keepAlive = "30m";

    /**
     * How many times each transcript is graded before a median is taken.
     *
     * <p>Three, because one was measurably not enough: 3 of 12 candidates scored differently across
     * two identical runs at temperature 0. Greedy decoding is not deterministic decoding.
     *
     * <p>It triples the cost of the slowest thing in the system - grading is ~91 s per call on a
     * two-thread pool - so verdicts land minutes after the interview. That is the right trade for
     * something that influences whether a person is hired, and the wrong one for a demo, which is
     * why it is configurable rather than fixed.
     */
    private int gradingSamples = 3;

    private int connectTimeoutSeconds = 10;

    private int readTimeoutSeconds = 120;

    private int writeTimeoutSeconds = 30;
}
