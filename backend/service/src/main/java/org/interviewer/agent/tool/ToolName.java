package org.interviewer.agent.tool;

import org.interviewer.agent.tool.dto.AskFollowupArgs;
import org.interviewer.agent.tool.dto.AskFollowupResult;
import org.interviewer.agent.tool.dto.FetchQuestionArgs;
import org.interviewer.agent.tool.dto.FetchQuestionResult;
import org.interviewer.agent.tool.dto.FinishInterviewArgs;
import org.interviewer.agent.tool.dto.FinishInterviewResult;
import org.interviewer.agent.tool.dto.RecordEvidenceArgs;
import org.interviewer.agent.tool.dto.RecordEvidenceResult;
import org.interviewer.agent.tool.dto.RunCodeArgs;
import org.interviewer.agent.tool.dto.RunCodeResult;
import org.interviewer.agent.tool.dto.ScoreResponseArgs;
import org.interviewer.agent.tool.dto.ScoreResponseResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The six tools the interviewer agent can call, and the single source of truth for which
 * tools exist.
 *
 * <p>The catalogue is an enum rather than "whatever {@code InterviewTool} beans happen to be on
 * the classpath" for one reason: the schema files must be loadable and checkable before any
 * implementation exists. Phase 1 ships the contracts; Phase 3 ships the implementations. Driving
 * schema compilation off the bean set would mean zero schemas are compiled, and therefore zero
 * schemas are validated, until the last phase that could still be wrong about them.
 *
 * <p>{@code ToolRegistry} cross-checks the two: every registered bean must name a constant here.
 */
public enum ToolName {

    FETCH_QUESTION("fetch_question", FetchQuestionArgs.class, FetchQuestionResult.class),
    ASK_FOLLOWUP("ask_followup", AskFollowupArgs.class, AskFollowupResult.class),
    SCORE_RESPONSE("score_response", ScoreResponseArgs.class, ScoreResponseResult.class),
    RUN_CODE("run_code", RunCodeArgs.class, RunCodeResult.class),
    RECORD_EVIDENCE("record_evidence", RecordEvidenceArgs.class, RecordEvidenceResult.class),
    FINISH_INTERVIEW("finish_interview", FinishInterviewArgs.class, FinishInterviewResult.class);

    private final String wireName;
    private final Class<?> argsType;
    private final Class<?> resultType;

    ToolName(String wireName, Class<?> argsType, Class<?> resultType) {
        this.wireName = wireName;
        this.argsType = argsType;
        this.resultType = resultType;
    }

    /**
     * The record the args schema deserializes into. Declared here so the catalogue is complete
     * before any {@link InterviewTool} exists — the drift tests need the schema/record pairing in
     * Phase 1, and an implementation that disagrees with it in Phase 3 is a bug worth failing on.
     */
    public Class<?> argsType() {
        return argsType;
    }

    public Class<?> resultType() {
        return resultType;
    }

    /** The name the model emits and the schema file is named after. Never the enum constant. */
    public String wireName() {
        return wireName;
    }

    /** {@code tools/fetch_question.json} */
    public String argsResourcePath() {
        return "tools/" + wireName + ".json";
    }

    /** {@code tools/fetch_question.result.json} */
    public String resultResourcePath() {
        return "tools/" + wireName + ".result.json";
    }

    public static Optional<ToolName> fromWireName(String name) {
        return Arrays.stream(values()).filter(t -> t.wireName.equals(name)).findFirst();
    }

    public static List<String> wireNames() {
        return Arrays.stream(values()).map(ToolName::wireName).toList();
    }
}
