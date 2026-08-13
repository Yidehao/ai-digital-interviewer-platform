package org.interviewer.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool catalogue and the schema files on disk must describe the same six tools.
 *
 * <p>This is the cheap half of the drift story: it turns "someone added a tool and forgot the
 * schema" — or shipped a schema for a tool that no longer exists — from an incident into a red
 * build. The expensive half, whether each schema's <em>types</em> match its record, is
 * {@code ToolSchemaDriftTest}.
 */
class ToolCatalogTest {

    private static final PathMatchingResourcePatternResolver RESOLVER =
            new PathMatchingResourcePatternResolver();

    @Test
    void everyToolHasBothSchemaFiles() {
        for (ToolName tool : ToolName.values()) {
            assertThat(RESOLVER.getResource("classpath:" + tool.argsResourcePath()).exists())
                    .as("missing args schema for %s", tool.wireName())
                    .isTrue();
            assertThat(RESOLVER.getResource("classpath:" + tool.resultResourcePath()).exists())
                    .as("missing result schema for %s", tool.wireName())
                    .isTrue();
        }
    }

    @Test
    void thereAreNoOrphanSchemaFiles() throws IOException {
        Set<String> onDisk = new TreeSet<>();
        // classpath*: not classpath: - the single-colon form does not expand a wildcard against
        // the classpath root and silently returns nothing, which would make this test pass by
        // finding no orphans because it found no files at all.
        for (Resource resource : RESOLVER.getResources("classpath*:tools/*.json")) {
            onDisk.add(resource.getFilename());
        }

        Set<String> expected = new TreeSet<>();
        for (ToolName tool : ToolName.values()) {
            expected.add(tool.wireName() + ".json");
            expected.add(tool.wireName() + ".result.json");
        }

        assertThat(onDisk)
                .as("a schema file exists for a tool that is not in ToolName, or vice versa")
                .isEqualTo(expected);
    }

    @Test
    void wireNamesAreSnakeCaseAndUnique() {
        List<String> names = ToolName.wireNames();

        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).allSatisfy(n ->
                assertThat(n).matches("[a-z]+(_[a-z]+)*"));
    }

    @Test
    void everyToolNameResolvesFromItsWireName() {
        for (ToolName tool : ToolName.values()) {
            assertThat(ToolName.fromWireName(tool.wireName())).contains(tool);
        }
        assertThat(ToolName.fromWireName("summarise_candidate")).isEmpty();
    }

    @Test
    void argsAndResultTypesAreDistinctRecords() {
        for (ToolName tool : ToolName.values()) {
            assertThat(tool.argsType()).as("%s args", tool.wireName()).isNotNull();
            assertThat(tool.resultType()).as("%s result", tool.wireName()).isNotNull();
            assertThat(tool.argsType()).isNotEqualTo(tool.resultType());
            assertThat(tool.argsType().isRecord())
                    .as("%s args type should be a record", tool.wireName()).isTrue();
            assertThat(tool.resultType().isRecord())
                    .as("%s result type should be a record", tool.wireName()).isTrue();
        }

        // No two tools may share a payload type - that would make a schema mismatch invisible.
        assertThat(Arrays.stream(ToolName.values()).map(ToolName::argsType).toList())
                .doesNotHaveDuplicates();
        assertThat(Arrays.stream(ToolName.values()).map(ToolName::resultType).toList())
                .doesNotHaveDuplicates();
    }
}
