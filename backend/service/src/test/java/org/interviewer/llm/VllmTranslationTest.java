package org.interviewer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.entity.ollama.ChatRequest;
import org.interviewer.entity.ollama.ChatResponse;
import org.interviewer.entity.ollama.ToolSpec;
import org.interviewer.utils.LlmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Translating between Ollama's dialect and OpenAI's, which vLLM speaks.
 *
 * <p>These differences are small enough to look like nothing and large enough to make every tool
 * call fail. The one that matters most is {@code arguments}: OpenAI types it as a <em>string</em>
 * containing JSON, and the loop's validator expects an object — so without the parse, every call
 * on the new backend would be rejected as schema-invalid for a reason that has nothing to do with
 * the model, and the 100%-argument-validity result would appear to collapse on a config change.
 */
class VllmTranslationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final VllmClient client = new VllmClient(null, new LlmProperties(), mapper);

    private JsonNode toOpenAi(ChatRequest request) throws Exception {
        Method m = VllmClient.class.getDeclaredMethod("toOpenAi", ChatRequest.class);
        m.setAccessible(true);
        return (JsonNode) m.invoke(client, request);
    }

    private ChatResponse fromOpenAi(String json) throws Exception {
        Method m = VllmClient.class.getDeclaredMethod("fromOpenAi", JsonNode.class);
        m.setAccessible(true);
        return (ChatResponse) m.invoke(client, mapper.readTree(json));
    }

    @Test
    @DisplayName("tool-call arguments arrive as a JSON string and become an object")
    void argumentsAreParsedFromTheirStringForm() throws Exception {
        ChatResponse response = fromOpenAi("""
            {"model":"qwen","choices":[{"finish_reason":"tool_calls","message":{
              "role":"assistant","content":null,"tool_calls":[
                {"id":"c1","type":"function","function":{
                   "name":"score_response",
                   "arguments":"{\\"dimension\\":\\"depth\\",\\"score\\":4}"}}]}}],
             "usage":{"prompt_tokens":1614,"completion_tokens":22}}
            """);

        assertThat(response.hasToolCalls()).isTrue();
        var call = response.getMessage().getToolCalls().get(0);
        assertThat(call.toolName()).isEqualTo("score_response");
        // An object, not a TextNode. This is the assertion the whole class exists for.
        assertThat(call.arguments().isObject()).isTrue();
        assertThat(call.arguments().get("score").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("unparseable arguments are left for the ladder, not thrown")
    void malformedArgumentsFallThroughToSchemaValidation() throws Exception {
        // Rung 2 offers exactly one repair for invalid arguments, and it recovers 84% of them.
        // Throwing here would replace a handled case with an unhandled one.
        ChatResponse response = fromOpenAi("""
            {"choices":[{"message":{"role":"assistant","tool_calls":[
              {"function":{"name":"score_response","arguments":"not json at all"}}]}}]}
            """);
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.getMessage().getToolCalls().get(0).arguments().isObject()).isFalse();
    }

    @Test
    @DisplayName("sampling options move from options{} to the top level")
    void temperatureSurvivesTranslation() throws Exception {
        // Temperature 0 for the grader is what makes a verdict comparable across runs. Losing it
        // in translation would silently make every grading number noisier.
        ChatRequest request = ChatRequest.builder()
                .model("qwen").stream(false)
                .messages(List.of(ChatMessage.user("hello")))
                .options(Map.of("temperature", 0.0, "num_ctx", 8192))
                .build();

        assertThat(toOpenAi(request).path("temperature").asDouble()).isZero();
    }

    @Test
    @DisplayName("the verdict schema maps to guided_json")
    void constrainedDecodingIsTranslated() throws Exception {
        JsonNode schema = mapper.readTree("""
            {"type":"object","properties":{"overall":{"type":"integer","minimum":1,"maximum":5}}}
            """);
        ChatRequest request = ChatRequest.builder()
                .model("qwen").stream(false)
                .messages(List.of(ChatMessage.user("grade this")))
                .format(schema)
                .build();

        JsonNode body = toOpenAi(request);
        assertThat(body.has("guided_json")).isTrue();
        assertThat(body.path("guided_json").path("properties").path("overall").path("maximum")
                .asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("tool definitions pass through unchanged")
    void toolSpecsAreAlreadyOpenAiShaped() throws Exception {
        // ToolSpec was written as {type:function, function:{name, description, parameters}}, which
        // is OpenAI's shape and which Ollama also accepts - so the twelve schema files feed both
        // backends with no second representation to keep in step.
        ChatRequest request = ChatRequest.builder()
                .model("qwen").stream(false)
                .messages(List.of(ChatMessage.user("hi")))
                .tools(List.of(ToolSpec.of("fetch_question", "Draw the next question",
                        mapper.readTree("{\"type\":\"object\"}"))))
                .build();

        JsonNode tool = toOpenAi(request).path("tools").path(0);
        assertThat(tool.path("type").asText()).isEqualTo("function");
        assertThat(tool.path("function").path("name").asText()).isEqualTo("fetch_question");
    }

    @Test
    @DisplayName("token usage is carried across; timing is not available")
    void usageMapsAndTimingStaysNull() throws Exception {
        ChatResponse response = fromOpenAi("""
            {"choices":[{"message":{"role":"assistant","content":"ok"}}],
             "usage":{"prompt_tokens":1614,"completion_tokens":22}}
            """);
        assertThat(response.getPromptEvalCount()).isEqualTo(1614);
        assertThat(response.getEvalCount()).isEqualTo(22);
        // vLLM reports no prompt_eval_duration, so the one metric that actually tracks prompt cost
        // goes quiet on this backend. Better to know that than to read the silence as "free".
        assertThat(response.getPromptEvalDurationNanos()).isNull();
    }
}
