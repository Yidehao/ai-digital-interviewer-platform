package org.interviewer.agent.tool;

import org.interviewer.agent.tool.dto.FetchQuestionArgs;
import org.interviewer.agent.tool.dto.FetchQuestionResult;
import org.interviewer.agent.tool.dto.FinishInterviewArgs;
import org.interviewer.agent.tool.dto.FinishInterviewResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registry behaviour, against fake tools. The real implementations arrive in Phase 3; what has to
 * be true before then is that the registry routes by the name the model emits, refuses ambiguity,
 * and survives having nothing to register.
 */
class ToolRegistryTest {

    @Test
    void survivesHavingNoToolsAtAll() {
        // Phase 1 and 2 genuinely have zero implementations. If this throws, the application does
        // not start until Phase 3, which would make the intervening phases untestable.
        ToolRegistry registry = registryOf();

        assertThat(registry.size()).isZero();
        assertThat(registry.names()).isEmpty();
        assertThat(registry.find("fetch_question")).isEmpty();
    }

    @Test
    void routesByTheNameTheModelEmits() {
        FakeFetchQuestion fetch = new FakeFetchQuestion();
        ToolRegistry registry = registryOf(fetch, new FakeFinishInterview());

        assertThat(registry.find("fetch_question")).containsSame(fetch);
        assertThat(registry.find(ToolName.FETCH_QUESTION)).containsSame(fetch);
        assertThat(registry.names())
                .containsExactlyInAnyOrder("fetch_question", "finish_interview");
    }

    @Test
    void unknownToolNameIsEmptyRatherThanAnException() {
        // Rung 3 of the fallback ladder answers a hallucinated tool name with a structured error
        // listing the real ones. That is only possible if lookup returns empty instead of throwing.
        ToolRegistry registry = registryOf(new FakeFetchQuestion());

        assertThat(registry.find("summarise_candidate")).isEmpty();
        assertThat(registry.find("FETCH_QUESTION")).isEmpty();
    }

    @Test
    void refusesTwoToolsClaimingTheSameName() {
        assertThatThrownBy(() -> registryOf(new FakeFetchQuestion(), new FakeFetchQuestion()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fetch_question");
    }

    @Test
    void refusesAToolWithNoName() {
        assertThatThrownBy(() -> registryOf(new NamelessTool()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NamelessTool");
    }

    @Test
    void onlyFinishInterviewIsTerminal() {
        // Termination is an explicit action, not the absence of a tool call. A second terminal
        // tool would make "no tool call emitted" ambiguous, and the fallback claim rests on it
        // being unambiguous.
        assertThat(new FakeFetchQuestion().terminal()).isFalse();
        assertThat(new FakeFinishInterview().terminal()).isTrue();
    }

    @Test
    void defaultTimeoutIsTheLoopsToEnforce() {
        assertThat(new FakeFetchQuestion().timeoutMs()).isEqualTo(3_000L);
    }

    // ------------------------------------------------------------------ fakes and plumbing

    private static ToolRegistry registryOf(InterviewTool<?, ?>... tools) {
        return new ToolRegistry(new FixedProvider(Arrays.asList(tools)));
    }

    /** Minimal ObjectProvider: the registry only ever calls orderedStream(). */
    private record FixedProvider(List<InterviewTool<?, ?>> tools)
            implements ObjectProvider<InterviewTool<?, ?>> {

        @Override
        public Stream<InterviewTool<?, ?>> orderedStream() {
            return tools.stream();
        }

        @Override
        public Stream<InterviewTool<?, ?>> stream() {
            return tools.stream();
        }

        @Override
        public InterviewTool<?, ?> getObject() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InterviewTool<?, ?> getObject(Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InterviewTool<?, ?> getIfAvailable() {
            return null;
        }

        @Override
        public InterviewTool<?, ?> getIfUnique() {
            return null;
        }
    }

    private static class FakeFetchQuestion
            implements InterviewTool<FetchQuestionArgs, FetchQuestionResult> {

        @Override
        public ToolName name() {
            return ToolName.FETCH_QUESTION;
        }

        @Override
        public Class<FetchQuestionArgs> argsType() {
            return FetchQuestionArgs.class;
        }

        @Override
        public Class<FetchQuestionResult> resultType() {
            return FetchQuestionResult.class;
        }

        @Override
        public FetchQuestionResult execute(FetchQuestionArgs args, ToolContext ctx) {
            return new FetchQuestionResult(null, null, null, null, true, 0);
        }
    }

    private static class FakeFinishInterview
            implements InterviewTool<FinishInterviewArgs, FinishInterviewResult> {

        @Override
        public ToolName name() {
            return ToolName.FINISH_INTERVIEW;
        }

        @Override
        public Class<FinishInterviewArgs> argsType() {
            return FinishInterviewArgs.class;
        }

        @Override
        public Class<FinishInterviewResult> resultType() {
            return FinishInterviewResult.class;
        }

        @Override
        public boolean terminal() {
            return true;
        }

        @Override
        public FinishInterviewResult execute(FinishInterviewArgs args, ToolContext ctx) {
            return new FinishInterviewResult(true, args.reason(), false, 0, false);
        }
    }

    private static class NamelessTool
            implements InterviewTool<FetchQuestionArgs, FetchQuestionResult> {

        @Override
        public ToolName name() {
            return null;
        }

        @Override
        public Class<FetchQuestionArgs> argsType() {
            return FetchQuestionArgs.class;
        }

        @Override
        public Class<FetchQuestionResult> resultType() {
            return FetchQuestionResult.class;
        }

        @Override
        public FetchQuestionResult execute(FetchQuestionArgs args, ToolContext ctx) {
            throw new UnsupportedOperationException();
        }
    }
}
