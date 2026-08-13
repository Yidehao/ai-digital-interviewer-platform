package org.interviewer.agent.tool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every {@link InterviewTool} bean on the classpath, addressable by the name the model emits.
 *
 * <p>Empty until Phase 3, which is legal and logged. What is not legal, and fails startup:
 * a tool whose name is not in {@link ToolName}, or two tools claiming the same name. Both are the
 * kind of mistake that would otherwise surface mid-interview as an unroutable tool call.
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<ToolName, InterviewTool<?, ?>> byName = new EnumMap<>(ToolName.class);

    /**
     * {@code ObjectProvider} rather than {@code List}: Spring treats a required collection
     * dependency with no candidates as a missing bean and fails the context. Until Phase 3 there
     * are legitimately zero tools, and startup must survive that.
     */
    public ToolRegistry(ObjectProvider<InterviewTool<?, ?>> tools) {
        for (InterviewTool<?, ?> tool : tools.orderedStream().toList()) {
            ToolName name = tool.name();
            if (name == null) {
                throw new IllegalStateException(
                        tool.getClass().getName() + " returned a null name()");
            }
            InterviewTool<?, ?> clash = byName.putIfAbsent(name, tool);
            if (clash != null) {
                throw new IllegalStateException("Two tools claim the name '" + name.wireName()
                        + "': " + clash.getClass().getName() + " and " + tool.getClass().getName());
            }
        }
    }

    @PostConstruct
    void logRegistered() {
        if (byName.isEmpty()) {
            log.info("Tool registry empty - schemas are loaded and checked, "
                    + "implementations land in Phase 3");
            return;
        }
        List<String> registered = names();
        log.info("Registered {} of {} interview tools: {}",
                byName.size(), ToolName.values().length, registered);
        List<String> missing = ToolName.wireNames().stream()
                .filter(n -> !registered.contains(n))
                .toList();
        if (!missing.isEmpty()) {
            log.warn("Tools declared in ToolName but not implemented: {}", missing);
        }
    }

    /** Empty when the model emitted a name we do not serve - rung 3 of the fallback ladder. */
    public Optional<InterviewTool<?, ?>> find(String wireName) {
        return ToolName.fromWireName(wireName).map(byName::get);
    }

    public Optional<InterviewTool<?, ?>> find(ToolName name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Registered names, in declaration order. Fed back to the model on an unknown-tool error. */
    public List<String> names() {
        return byName.keySet().stream().map(ToolName::wireName).toList();
    }

    public List<InterviewTool<?, ?>> all() {
        return List.copyOf(byName.values());
    }

    public int size() {
        return byName.size();
    }
}
