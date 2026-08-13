package org.interviewer.llm;

import org.interviewer.entity.ollama.ChatRequest;
import org.interviewer.entity.ollama.ChatResponse;

/**
 * Talking to the model.
 *
 * <p>This is an interface for one reason: <b>so that {@code FakeOllamaClient} can exist.</b>
 * Every fallback-ladder test needs a model that misbehaves on demand — one that writes prose
 * instead of calling a tool, that emits invalid arguments twice running, that names a tool we do
 * not serve, that repeats itself, that refuses to ever finish. None of those are reliably
 * producible from a real model, and a test suite that cannot produce them is not testing the
 * ladder, it is hoping.
 *
 * <p>The other consequence is that the whole loop runs headless, with no HTTP and no GPU, in
 * milliseconds. That is what makes it worth writing forty tests instead of four.
 */
public interface OllamaClient {

    /**
     * One non-streaming chat completion.
     *
     * @throws ModelUnavailableException when the model could not be reached at all. That is rung 9
     *         of the fallback ladder — degrade to the scripted pipeline — and it is deliberately a
     *         distinct exception from "the model answered with something unusable", which is
     *         rungs 1 through 6 and is not a failure of the model's availability.
     */
    ChatResponse chat(ChatRequest request);

    /** Whether the model is reachable right now. Used at session start to warm the prefix cache. */
    boolean isAvailable();
}
