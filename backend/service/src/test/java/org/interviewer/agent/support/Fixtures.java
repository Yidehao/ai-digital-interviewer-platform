package org.interviewer.agent.support;

import org.interviewer.agent.tool.ToolName;
import org.interviewer.entity.agent.InterviewSession;

import java.time.Instant;

/**
 * Canonical result documents that satisfy the real result schemas, plus a session to run against.
 *
 * <p>These are schema-valid on purpose: a fake that returned {@code {}} would make every test pass
 * through rung 5 without anyone noticing that nothing else was being exercised.
 */
public final class Fixtures {

    public static final String QUESTION_RESULT = """
            {"questionId":"q-1","question":"How would you cache a read-heavy endpoint?",
             "topic":"caching","difficulty":"medium",
             "exhausted":false,"remaining":4}""";

    public static final String EXHAUSTED_RESULT = """
            {"questionId":null,"question":null,"topic":null,"difficulty":null,
             "exhausted":true,"remaining":0}""";

    public static final String FOLLOWUP_RESULT = """
            {"delivered":true,"turnId":"t-1","text":"What invalidation strategy did you use?",
             "followupCount":1,"reason":null}""";

    public static final String SCORE_RESULT = """
            {"recorded":true,"questionId":"q-1","dimension":"depth","score":3,
             "supersededPrevious":false,"dimensionsScored":1}""";

    public static final String FINISH_RESULT = """
            {"finished":true,"reason":"complete","alreadyFinished":false,
             "questionsAsked":3,"gradingQueued":true}""";

    /** Violates finish_interview.result.json - questionsAsked is required and absent. */
    public static final String MALFORMED_FINISH_RESULT = """
            {"finished":true,"reason":"complete","alreadyFinished":false,"gradingQueued":true}""";

    public static InterviewSession session() {
        InterviewSession session = new InterviewSession();
        session.setSessionId("s-test-1");
        session.setCandidateId("c-1");
        session.setJobId("j-1");
        session.setSystemPrompt("You are conducting a technical job interview.");
        session.setStartedAt(Instant.parse("2026-08-13T09:00:00Z"));
        return session;
    }

    /** A gateway serving the four tools a normal interview uses. */
    public static FakeToolGateway happyGateway() {
        return new FakeToolGateway()
                .register(ToolName.FETCH_QUESTION, QUESTION_RESULT)
                .register(ToolName.ASK_FOLLOWUP, FOLLOWUP_RESULT)
                .register(ToolName.SCORE_RESPONSE, SCORE_RESULT)
                .register(ToolName.FINISH_INTERVIEW, true, 3_000L,
                        args -> FakeToolGateway.read(FINISH_RESULT));
    }

    private Fixtures() {
    }
}
