package org.interviewer.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.entity.ollama.ChatRequest;
import org.interviewer.entity.ollama.ChatResponse;
import org.interviewer.entity.ollama.ToolCall;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A model that answers instantly, for load testing.
 *
 * <p><b>Why the model is stubbed, and why that makes the number more useful rather than less.</b>
 * With the real model, a concurrency test measures Ollama: one 7B model on one GPU serialises
 * requests, so the curve would flatten at 1-2 concurrent sessions and tell you nothing about the
 * application. What is actually worth knowing is how many sessions the <em>app tier</em> sustains —
 * emitters, thread pools, tool dispatch, Redis, the database — because that is the part this
 * project built and the part that would be scaled differently.
 *
 * <p>Any figure produced with this client must therefore be reported as <b>app tier, model
 * stubbed</b>. Quoting it as end-to-end capacity would be claiming throughput the GPU cannot
 * deliver.
 *
 * <p>{@code think-millis} simulates generation latency so the pools are exercised realistically
 * rather than with zero-cost calls. Default 50 ms — enough to make threads actually wait, small
 * enough that the app tier rather than the sleep is the bottleneck. Set it to 7000 to model a cold
 * prompt.
 */
@Slf4j
@Primary
@Profile("loadtest")
@Component
public class MockOllamaClient implements OllamaClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicLong calls = new AtomicLong();

    @Value("${interviewer.loadtest.think-millis:50}")
    private long thinkMillis;

    /** Turns per interview before the mock finishes, so sessions have a realistic lifetime. */
    @Value("${interviewer.loadtest.turns-per-session:6}")
    private int turnsPerSession;

    @Override
    public ChatResponse chat(ChatRequest request) {
        long n = calls.incrementAndGet();

        if (thinkMillis > 0) {
            try {
                // Jitter, because a fixed sleep makes every session tick in lockstep and produces
                // a thundering herd that is an artefact of the harness rather than of the system.
                long jitter = ThreadLocalRandom.current().nextLong(thinkMillis / 2 + 1);
                Thread.sleep(thinkMillis + jitter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Count this session's turns by how much conversation it has accumulated. Cheap, and it
        // gives each session a bounded life without the mock holding per-session state.
        int turns = request.getMessages() == null ? 0 : request.getMessages().size();
        boolean shouldFinish = turns >= turnsPerSession * 2;

        ChatResponse response = new ChatResponse();
        response.setMessage(ChatMessage.assistantToolCalls(List.of(
                shouldFinish ? finishCall() : fetchQuestionCall())));
        response.setDone(true);
        // Realistic token counts so the accounting path is exercised rather than skipped.
        response.setPromptEvalCount(1500 + turns * 120);
        response.setEvalCount(40);
        return response;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    public long callCount() {
        return calls.get();
    }

    private ToolCall fetchQuestionCall() {
        return ToolCall.of("fetch_question", MAPPER.createObjectNode());
    }

    private ToolCall finishCall() {
        var args = MAPPER.createObjectNode();
        args.put("reason", "complete");
        args.put("closingMessage", "Thank you for your time. This concludes the interview.");
        return ToolCall.of("finish_interview", args);
    }
}
