package org.interviewer.agent.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interviewer.agent.tool.ToolName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * B6 — the schemas and the Java records must not drift apart.
 *
 * <p>The catalogue test next door compares tool <em>names</em> to schema <em>files</em>. That
 * catches "someone added a tool and forgot the schema" and nothing else. It would not notice a
 * schema declaring {@code score} as a string while {@code ScoreResponseArgs} declares an
 * {@code int} — a mismatch that surfaces at runtime, mid-interview, as a deserialization error the
 * model cannot repair because the model did nothing wrong.
 *
 * <p>Each case round-trips a complete example instance:
 *
 * <pre>
 *   example JSON  --validate-->  schema
 *                 --read------>  record        (fails if the schema has a field the record lacks,
 *                                               or a type the record cannot hold)
 *                 --write----->  JSON
 *                 --validate-->  schema        (fails if the record has a field the schema forbids,
 *                                               since additionalProperties is false)
 *                 --compare--->  example       (fails if anything was silently dropped or coerced)
 * </pre>
 *
 * <p>Both directions matter. Reading alone would miss a record field the schema does not know
 * about; writing alone would miss a schema field the record quietly ignores.
 */
class ToolSchemaDriftTest {

    /**
     * Not the Spring mapper. Boot disables {@code FAIL_ON_UNKNOWN_PROPERTIES}, and that setting is
     * exactly the drift this test exists to catch — with it off, a schema field the record has
     * forgotten is silently discarded and the test passes while production loses data.
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ToolSchemas schemas;

    @BeforeEach
    void setUp() {
        schemas = new ToolSchemas(new ObjectMapper());
        schemas.compile();
    }

    @ParameterizedTest(name = "{0} args round-trip")
    @EnumSource(ToolName.class)
    void argsExampleRoundTripsThroughItsRecord(ToolName tool) {
        roundTrip(tool, tool.argsType(), example(tool, "args"), Side.ARGS);
    }

    @ParameterizedTest(name = "{0} result round-trip")
    @EnumSource(ToolName.class)
    void resultExampleRoundTripsThroughItsRecord(ToolName tool) {
        roundTrip(tool, tool.resultType(), example(tool, "result"), Side.RESULT);
    }

    private enum Side { ARGS, RESULT }

    private void roundTrip(ToolName tool, Class<?> recordType, JsonNode example, Side side) {
        List<String> before = validate(tool, example, side);
        assertThat(before)
                .as("the example instance for %s (%s) must itself be schema-valid, "
                        + "or this test proves nothing", tool.wireName(), side)
                .isEmpty();

        Object instance;
        try {
            instance = mapper.treeToValue(example, recordType);
        } catch (Exception e) {
            fail("%s %s schema does not deserialize into %s - the schema and the record have "
                            + "drifted: %s",
                    tool.wireName(), side, recordType.getSimpleName(), e.getMessage());
            return;
        }

        // Via text, not valueToTree. valueToTree maps a `long` field to a LongNode while the
        // example's 412 parses to an IntNode, and JsonNode equality is node-type sensitive - so
        // the shapes would compare unequal while being identical on the wire. Round-tripping
        // through the serialized form compares what actually reaches the model.
        JsonNode reserialized = reread(instance);

        assertThat(validate(tool, reserialized, side))
                .as("%s serialized back from %s no longer satisfies its own schema - the record "
                                + "has a field the schema forbids, or lost one it requires",
                        tool.wireName(), recordType.getSimpleName())
                .isEmpty();

        assertThat(reserialized)
                .as("%s %s changed shape on the way through %s - a field was dropped, added, "
                                + "or coerced to a different type",
                        tool.wireName(), side, recordType.getSimpleName())
                .isEqualTo(example);
    }

    private JsonNode reread(Object instance) {
        try {
            return mapper.readTree(mapper.writeValueAsString(instance));
        } catch (Exception e) {
            throw new IllegalStateException("could not serialize " + instance, e);
        }
    }

    private List<String> validate(ToolName tool, JsonNode node, Side side) {
        return side == Side.ARGS
                ? schemas.validateArgs(tool, node)
                : schemas.validateResult(tool, node);
    }

    private JsonNode example(ToolName tool, String side) {
        String path = "tools/examples/" + tool.wireName() + "." + side + ".json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("missing example instance: %s", path).isNotNull();
            return mapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + path, e);
        }
    }
}
