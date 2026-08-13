package org.interviewer.agent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.tool.ToolName;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The twelve tool schemas, compiled once at startup.
 *
 * <p>The schemas are classpath resources rather than something generated from the Java records.
 * Three consumers read the same files — the agent loop, the {@code tools[]} array sent to Ollama,
 * and the MCP bridge — and generating them from classes would couple the wire contract to Java
 * refactors, handing MCP clients a schema that shifts whenever a field is renamed. The records
 * follow the schemas, not the other way round; {@code ToolSchemaDriftTest} is what keeps that
 * true.
 *
 * <p>A missing or malformed schema is a <b>startup failure</b>. The alternative is discovering it
 * on the turn that first uses that tool, mid-interview, in front of a candidate.
 *
 * <p>Validation runs in both directions. Validating arguments protects the loop from the model and
 * produces the repair message on rung 2 of the fallback ladder. Validating results protects the
 * model from our own bugs: a malformed result is not something the model can repair, so it is
 * logged as an internal error and reported as one. {@code side=result} rejections should sit flat
 * at zero in production — if that counter moves, we broke something.
 */
@Slf4j
@Component
public class ToolSchemas {

    private final ObjectMapper objectMapper;

    private final Map<ToolName, JsonSchema> argsSchemas = new EnumMap<>(ToolName.class);
    private final Map<ToolName, JsonSchema> resultSchemas = new EnumMap<>(ToolName.class);
    private final Map<ToolName, JsonNode> argsDocuments = new EnumMap<>(ToolName.class);

    public ToolSchemas(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void compile() {
        JsonSchemaFactory factory =
                JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        for (ToolName tool : ToolName.values()) {
            JsonNode args = read(tool.argsResourcePath());
            JsonNode result = read(tool.resultResourcePath());

            argsDocuments.put(tool, args);
            argsSchemas.put(tool, factory.getSchema(args));
            resultSchemas.put(tool, factory.getSchema(result));
        }
        log.info("Compiled {} tool schemas ({} args + {} result) for: {}",
                argsSchemas.size() + resultSchemas.size(),
                argsSchemas.size(), resultSchemas.size(), ToolName.wireNames());
    }

    private JsonNode read(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("Tool schema missing from the classpath: "
                    + resourcePath + ". Every ToolName needs both an args and a result schema.");
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Tool schema is not readable JSON: " + resourcePath, e);
        }
    }

    /**
     * Validate arguments the model produced.
     *
     * @return empty when valid; otherwise messages sorted for stable output, suitable for handing
     *         back to the model verbatim as the rung-2 repair prompt. They name the offending
     *         field, which is what makes a single repair turn work.
     */
    public List<String> validateArgs(ToolName tool, JsonNode args) {
        return messages(schemaFor(argsSchemas, tool, "args").validate(args));
    }

    /** Validate a result we produced. Non-empty means our bug, not the model's. */
    public List<String> validateResult(ToolName tool, JsonNode result) {
        return messages(schemaFor(resultSchemas, tool, "result").validate(result));
    }

    /**
     * The raw args schema document, for building the Ollama {@code tools[]} array and the MCP tool
     * listing in later phases.
     *
     * <p>Callers that put this on the wire should strip the annotation-only keywords
     * ({@code $schema}, {@code $id}, {@code title}, top-level {@code description}) — the model
     * needs the parameter shape, and the benchmark that measured 100% argument validity did not
     * have those keys in front of it.
     */
    public JsonNode argsDocument(ToolName tool) {
        JsonNode doc = argsDocuments.get(tool);
        if (doc == null) {
            throw new IllegalStateException("No compiled args schema for " + tool.wireName());
        }
        return doc.deepCopy();
    }

    public JsonSchema argsSchema(ToolName tool) {
        return schemaFor(argsSchemas, tool, "args");
    }

    public JsonSchema resultSchema(ToolName tool) {
        return schemaFor(resultSchemas, tool, "result");
    }

    private JsonSchema schemaFor(Map<ToolName, JsonSchema> source, ToolName tool, String side) {
        JsonSchema schema = source.get(tool);
        if (schema == null) {
            throw new IllegalStateException(
                    "No compiled " + side + " schema for " + tool.wireName());
        }
        return schema;
    }

    private static List<String> messages(Set<ValidationMessage> raw) {
        return raw.stream()
                .map(ValidationMessage::getMessage)
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
