package org.interviewer.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.interviewer.entity.grading.Verdict;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Aspect 3 of 3 — grading, timed, with the score distribution recorded.
 *
 * <p>The distribution is here rather than in the grader because it is a property worth watching
 * over time rather than one the grader should think about. The cohort run showed the scale
 * compressing to about one point in practice — designed-weak averaged 2.25, designed-strong 3.25,
 * and every designed-mixed candidate scored exactly 3. If that compression tightens further in
 * production, this histogram is where it shows up, and a grader that has quietly collapsed to a
 * single value is worse than useless for a hiring decision while still looking like it works.
 *
 * <p>Also times the call: ~82 s per session on this hardware, which is what makes a 40-session
 * evaluation most of a working day and belongs in any capacity planning.
 */
@Slf4j
@Aspect
@Component
public class GradingAspect {

    private final MeterRegistry registry;

    public GradingAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("execution(* org.interviewer.grader.GraderAgent.grade(..))")
    public Object timeGrading(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        String outcome = "ok";
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Verdict verdict) {
                registry.summary("interview.grader.overall").record(verdict.overall());
                verdict.dimensions().forEach(d ->
                        registry.summary("interview.grader.dimension",
                                "name", d.name()).record(d.score()));
            }
            return result;
        } catch (Throwable t) {
            outcome = "error";
            throw t;
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000L;
            Timer.builder("interview.grader.duration")
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(ms, TimeUnit.MILLISECONDS);
            log.info("graded in {} ms ({})", ms, outcome);
        }
    }
}
