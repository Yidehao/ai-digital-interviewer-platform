package org.interviewer.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Who is allowed to reach the endpoints that expose candidate data.
 *
 * <p><b>Both of these default to the secure value.</b> An auth flag that defaults to "off" is a
 * production incident waiting for someone to forget a config file, and this system holds interview
 * transcripts and hiring assessments — the two things a candidate would least like to be public.
 */
@Data
@Component
@ConfigurationProperties(prefix = "interviewer.auth")
public class AuthProperties {

    /**
     * Shared secret for the review console, from {@code REVIEW_ADMIN_TOKEN}.
     *
     * <p><b>A shared token, not user accounts, and the difference is stated rather than hidden.</b>
     * The {@code admin} table is empty and this codebase has no password hashing anywhere, so a
     * login built here would mean inventing a user system — and a half-built one storing plaintext
     * passwords would be worse than none. A bearer token held by the operator is a legitimate
     * pattern for an internal tool and closes the actual hole today.
     *
     * <p>What it does not give you: per-reviewer identity, revocation of one person, or an audit
     * trail tied to a login. {@code verdict_review.reviewed_by} is therefore self-declared. Real
     * accounts are the next step and this is sized as a stopgap on purpose.
     *
     * <p>Blank means <b>refuse everything</b>. Failing closed is the point: an unset secret is a
     * misconfiguration, and treating it as "no auth required" is how these holes stay open.
     */
    private String adminToken = "";

    /**
     * Whether starting an interview requires the candidate's own login session.
     *
     * <p>True everywhere except the eval profile. Without it, candidate ids are enumerable from the
     * admin list, so anyone could start an interview as someone else — or worse, start one to
     * consume the single in-flight interview a real candidate is entitled to.
     *
     * <p>The eval harness turns this off because it drives interviews without an SMS login, and it
     * does so through an explicitly named profile rather than a silent default.
     */
    private boolean candidateSessionRequired = true;
}
