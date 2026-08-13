package org.interviewer.demo;

import lombok.extern.slf4j.Slf4j;
import org.interviewer.agent.AgentEvents;
import org.interviewer.agent.InterviewerAgent;
import org.interviewer.agent.NoOpAgentEvents;
import org.interviewer.agent.SystemPromptBuilder;
import org.interviewer.agent.session.SessionPersister;
import org.interviewer.entity.Candidate;
import org.interviewer.entity.Job;
import org.interviewer.entity.agent.InterviewSession;
import org.interviewer.entity.agent.SessionState;
import org.interviewer.entity.agent.Turn;
import org.interviewer.entity.agent.TurnKind;
import org.interviewer.entity.ollama.ChatMessage;
import org.interviewer.llm.OllamaClient;
import org.interviewer.service.CandidateService;
import org.interviewer.service.JobService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Runs two interviews against the real model and diffs their tool logs.
 *
 * <p>This is the Phase 3 demo, and it is the point at which "adaptive" stops being an
 * architectural claim and becomes something you can check. Two candidates, the same job, the same
 * question bank — but scripted answers of different quality. If the agent is adaptive, the two
 * tool logs differ: the weak answers should draw follow-ups the strong ones do not. <b>If the
 * sequences come out identical, the interview was not adaptive</b>, whatever the diagram says.
 *
 * <p>Activated by profile so it never runs in a normal boot:
 *
 * <pre>
 *   mvn -pl api spring-boot:run -Dspring-boot.run.profiles=agent-demo
 * </pre>
 *
 * <p>The answers are scripted rather than spoken because this is testing the loop, not the speech
 * path. Real audio arrives with the eval harness.
 */
@Slf4j
@Profile("agent-demo")
@Component
public class AgentDemoRunner implements ApplicationRunner {

    /** A candidate who gives thin answers. Should attract follow-ups. */
    private static final List<String> SHALLOW = List.of(
            "I would just add a cache in front of it, probably Redis or something.",
            "We used indexes. That normally makes queries faster.",
            "I usually just write tests for the main path and move on.",
            "I would ask someone more senior about it.",
            "It was hard but we figured it out eventually.");

    /** A candidate who gives specific, evidenced answers. Should need fewer probes. */
    private static final List<String> STRONG = List.of(
            "We put Redis in front of the read path with a 30 second TTL, and the hard part was "
                    + "invalidation - we ended up publishing invalidation events on write rather "
                    + "than relying on expiry, because stale pricing was worse than a cache miss.",
            "The query had a composite index on (tenant_id, created_at) but the ORM was ordering "
                    + "by updated_at, so it did a filesort over 400k rows. Adding the matching "
                    + "index took it from eight seconds to forty milliseconds.",
            "I test the boundaries and the error paths first, because the happy path usually gets "
                    + "exercised by everyone else anyway. For that service I had property tests "
                    + "over the parser and integration tests only for the two flows that touched "
                    + "money.",
            "I disagreed about moving to microservices at our size - four engineers and one "
                    + "deployable. I wrote up the operational cost, we agreed to split only the "
                    + "ingest path, and that turned out to be the right boundary.",
            "The hardest bug was a race in our idempotency check. Two retries landed in the same "
                    + "millisecond and both passed the exists check before either wrote. I found "
                    + "it by logging the request id with a monotonic counter and diffing the two "
                    + "orderings.");

    private final InterviewerAgent agent;
    private final SessionPersister persister;
    private final SystemPromptBuilder promptBuilder;
    private final CandidateService candidateService;
    private final JobService jobService;
    private final OllamaClient llm;
    private final Clock clock;

    public AgentDemoRunner(InterviewerAgent agent,
                           SessionPersister persister,
                           SystemPromptBuilder promptBuilder,
                           CandidateService candidateService,
                           JobService jobService,
                           OllamaClient llm,
                           Clock clock) {
        this.agent = agent;
        this.persister = persister;
        this.promptBuilder = promptBuilder;
        this.candidateService = candidateService;
        this.jobService = jobService;
        this.llm = llm;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!llm.isAvailable()) {
            log.error("Ollama is not reachable - start it with `ollama serve` and re-run. "
                    + "(The loop would degrade to the scripted path, which is correct behaviour "
                    + "but not what this demo is trying to show.)");
            return;
        }

        InterviewSession shallow = runOne("Demo Alpha", SHALLOW);
        InterviewSession strong = runOne("Demo Beta", STRONG);

        if (shallow != null && strong != null) {
            printDiff(shallow, strong);
        }
    }

    private InterviewSession runOne(String candidateName, List<String> answers) {
        Candidate candidate = findCandidate(candidateName);
        if (candidate == null) {
            log.error("no candidate named '{}' - run eval/seed_demo_data.py first", candidateName);
            return null;
        }
        Job job = jobService.getDetail(candidate.getJobId());
        if (job == null) {
            log.error("candidate {} has no job", candidateName);
            return null;
        }

        InterviewSession session = new InterviewSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setCandidateId(candidate.getId());
        session.setJobId(job.getId());
        session.setInterviewerId(job.getInterviewerId());
        session.setStartedAt(clock.instant());
        // Built once. Reused as the identical byte string on every turn, which is what keeps the
        // prefix cache warm and turns a ~20 s prompt evaluation into ~0.3 s.
        session.setSystemPrompt(promptBuilder.build(job.getPrompt(), 5));

        AgentEvents events = new ScriptedCandidate(session, answers, promptBuilder, clock);
        long started = System.currentTimeMillis();
        agent.run(session, events);
        long elapsed = System.currentTimeMillis() - started;

        log.info("=== {} finished: state={} turns={} tools={} errors={} in {} ms ===",
                candidateName, session.getState(), session.getTurnCount(),
                session.getToolCallCount(), session.getErrorCount(), elapsed);
        log.info("    tokens: {} prompt / {} completion",
                session.getPromptTokens(), session.getCompletionTokens());

        printTranscript(candidateName, session);

        try {
            persister.persist(session);
        } catch (Exception e) {
            log.error("could not persist session {}", session.getSessionId(), e);
        }
        return session;
    }

    /** Looked up by the mobile numbers eval/seed_demo_data.py assigns. */
    private Candidate findCandidate(String realName) {
        String mobile = "Demo Alpha".equals(realName) ? "19000000001" : "19000000002";
        return candidateService.queryMobileIsExist(mobile);
    }

    /**
     * Feeds the next scripted answer whenever the interviewer puts a question.
     *
     * <p>Implemented as {@link AgentEvents} rather than by pre-seeding the conversation, because
     * that is how a real candidate arrives: the loop asks, then waits. Pre-loading every answer up
     * front would test a different system.
     */
    private record ScriptedCandidate(InterviewSession session,
                                     List<String> answers,
                                     SystemPromptBuilder promptBuilder,
                                     Clock clock) implements AgentEvents {

        @Override
        public void onQuestion(Turn turn, String videoSrc) {
            answer();
        }

        @Override
        public void onFollowup(Turn turn) {
            answer();
        }

        private void answer() {
            int used = (int) session.getTurns().stream()
                    .filter(t -> t.getKind() == TurnKind.ANSWER).count();
            String text = answers.get(Math.min(used, answers.size() - 1));
            session.addTurn(TurnKind.ANSWER, null, text, clock.instant());
            // Wrapped in delimiters: transcribed speech is the one input the system cannot vet.
            session.getMessages().add(ChatMessage.user(promptBuilder.wrapAnswer(text)));
        }
    }

    private void printTranscript(String who, InterviewSession session) {
        StringBuilder out = new StringBuilder("\n--- transcript: " + who + " ---\n");
        for (Turn turn : session.transcript()) {
            out.append(String.format("  [%d] %-8s %s%n", turn.getSeq(), turn.getKind(),
                    truncate(turn.getText())));
        }
        log.info(out.toString());
    }

    /**
     * The whole point of the demo. Same job, same bank, different answers — do the tool logs
     * differ?
     */
    private void printDiff(InterviewSession shallow, InterviewSession strong) {
        StringBuilder out = new StringBuilder("\n=== TOOL LOG DIFF ===\n");
        out.append(String.format("  %-28s | %s%n", "Demo Alpha (thin answers)",
                "Demo Beta (specific answers)"));
        out.append("  ").append("-".repeat(62)).append("\n");

        List<String> a = shallow.getToolLog().stream()
                .map(InterviewSession.ToolRecord::toolName).toList();
        List<String> b = strong.getToolLog().stream()
                .map(InterviewSession.ToolRecord::toolName).toList();

        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            out.append(String.format("  %-28s | %s%n",
                    i < a.size() ? a.get(i) : "", i < b.size() ? b.get(i) : ""));
        }

        out.append("\n  follow-ups asked: ")
                .append(shallow.totalFollowups()).append(" vs ").append(strong.totalFollowups())
                .append("\n  tool calls:       ")
                .append(a.size()).append(" vs ").append(b.size())
                .append("\n  identical?        ").append(a.equals(b));
        if (a.equals(b)) {
            out.append("\n  >> identical tool logs. On this run the interview did not adapt.");
        }
        log.info(out.toString());
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 90 ? text : text.substring(0, 90) + "...";
    }
}
