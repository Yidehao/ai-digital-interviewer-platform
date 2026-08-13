package org.interviewer.agent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.agent.tool.ToolName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the validator does with arguments a model might actually produce.
 *
 * <p>Every failure case here was observed in the Phase 0.5 benchmark, not invented: an out-of-range
 * score, a missing required field, a plausible-but-wrong field name. The point is that each one
 * produces a message naming the offending field, because that message is handed back to the model
 * verbatim as the rung-2 repair prompt and a single repair turn only works if it says what to fix.
 */
class ToolSchemasTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private ToolSchemas schemas;

    @BeforeEach
    void setUp() {
        schemas = new ToolSchemas(mapper);
        schemas.compile();
    }

    @Test
    void compilesAllTwelveSchemas() {
        for (ToolName tool : ToolName.values()) {
            assertThat(schemas.argsSchema(tool)).as("%s args", tool.wireName()).isNotNull();
            assertThat(schemas.resultSchema(tool)).as("%s result", tool.wireName()).isNotNull();
        }
    }

    @Test
    void acceptsWellFormedArguments() {
        assertThat(schemas.validateArgs(ToolName.SCORE_RESPONSE, json("""
                {"questionId":"q-1","dimension":"depth","score":4,
                 "evidence":"Explained the tradeoff without prompting.","confidence":"high"}
                """))).isEmpty();
    }

    @Test
    void acceptsArgumentsOmittingEveryOptionalField() {
        // v2 cut score_response's required set to four fields. A call without `confidence` must
        // pass without a repair turn - that change alone was worth 17 of 60 benchmark calls.
        assertThat(schemas.validateArgs(ToolName.SCORE_RESPONSE, json("""
                {"questionId":"q-1","dimension":"depth","score":4,"evidence":"Clear reasoning."}
                """))).isEmpty();

        // fetch_question requires nothing at all.
        assertThat(schemas.validateArgs(ToolName.FETCH_QUESTION, json("{}"))).isEmpty();
    }

    @Test
    void rejectsScoreOutsideTheRubricRange() {
        // The single most common v1 failure: 12 of 60 calls scored above 5.
        List<String> errors = schemas.validateArgs(ToolName.SCORE_RESPONSE, json("""
                {"questionId":"q-1","dimension":"depth","score":7,"evidence":"Strong."}
                """));

        assertThat(errors).isNotEmpty();
        assertThat(String.join(" ", errors)).contains("score");
    }

    @Test
    void rejectsMissingRequiredFieldAndNamesIt() {
        List<String> errors = schemas.validateArgs(ToolName.ASK_FOLLOWUP, json("""
                {"question":"Why that approach?"}
                """));

        assertThat(errors).isNotEmpty();
        assertThat(String.join(" ", errors)).contains("parentQuestionId");
    }

    @Test
    void rejectsAPlausibleButHallucinatedField() {
        // additionalProperties:false is what turns an invented argument into a correctable error
        // rather than one the tool silently ignores. `difficulty_level` is a near-miss for the
        // real `difficulty`, which is exactly the shape hallucinated fields take.
        List<String> errors = schemas.validateArgs(ToolName.FETCH_QUESTION, json("""
                {"difficulty_level":"medium"}
                """));

        assertThat(errors).isNotEmpty();
        assertThat(String.join(" ", errors)).contains("difficulty_level");
    }

    @Test
    void rejectsAnOutOfEnumReasonButAcceptsTheOneTheModelInvented() {
        // The model kept emitting `time_exhausted` where v1 offered only `budget`, and kept doing
        // it after being handed the valid values. v2 accepts both; nonsense is still rejected.
        assertThat(schemas.validateArgs(ToolName.FINISH_INTERVIEW, json("""
                {"reason":"time_exhausted","closingMessage":"We are out of time. Thanks."}
                """))).isEmpty();

        assertThat(schemas.validateArgs(ToolName.FINISH_INTERVIEW, json("""
                {"reason":"got_bored","closingMessage":"Bye."}
                """))).isNotEmpty();
    }

    @Test
    void validatesResultsToo() {
        // Output validation catches our bugs, not the model's. A run_code result missing
        // durationMs is a shape the model cannot repair and must never be sent.
        assertThat(schemas.validateResult(ToolName.RUN_CODE, json("""
                {"stdout":"55\\n","stderr":"","exitCode":0,"timedOut":false,
                 "truncated":false,"durationMs":412}
                """))).isEmpty();

        assertThat(schemas.validateResult(ToolName.RUN_CODE, json("""
                {"stdout":"55\\n","stderr":"","exitCode":0,"timedOut":false,"truncated":false}
                """))).isNotEmpty();
    }

    @Test
    void argsDocumentIsACopyCallersCannotCorrupt() {
        // Three consumers read these documents - the loop, the Ollama tools[] array and the MCP
        // bridge. One of them mutating the shared node would silently change the contract for the
        // other two.
        JsonNode first = schemas.argsDocument(ToolName.FETCH_QUESTION);
        ((com.fasterxml.jackson.databind.node.ObjectNode) first).put("title", "tampered");

        assertThat(schemas.argsDocument(ToolName.FETCH_QUESTION).get("title").asText())
                .isEqualTo("fetch_question");
    }

    @Test
    void unknownToolHasNoSchema() {
        assertThatThrownBy(() -> new ToolSchemas(mapper).argsSchema(ToolName.FETCH_QUESTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fetch_question");
    }

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
