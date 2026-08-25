package org.interviewer.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.interviewer.base.BaseInfoProperties;
import org.interviewer.utils.AuthProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Guards the endpoints that expose candidate data.
 *
 * <p>Before this, {@code /review/**} was <b>completely unauthenticated</b>: every interview
 * transcript and every hiring assessment in the system was readable by anyone who could reach the
 * port. {@code /interview/{candidateId}/stream} was equally open, and candidate ids are enumerable
 * from the admin list — so anyone could start an interview as someone else, or simply consume the
 * one in-flight interview a real candidate is allowed and lock them out of their own.
 *
 * <p><b>Fails closed.</b> A blank admin token refuses every request rather than allowing them. An
 * unset secret is a misconfiguration, and the habit of reading it as "auth not required here" is
 * exactly how this class of hole survives into production.
 *
 * <p><b>Constant-time comparison</b> on the token. The timing signal from {@code String.equals} on
 * a secret is small and entirely avoidable, and "small" is not an argument worth making about a
 * system holding hiring decisions.
 *
 * <p>What this does not solve, stated plainly: the rest of the admin API — candidates, jobs,
 * questions, interviewers — remains unauthenticated. That is a pre-existing condition of this
 * codebase rather than something introduced here, and fixing it properly means building the user
 * system the empty {@code admin} table implies.
 */
@Slf4j
@Component
public class AuthInterceptor extends BaseInfoProperties implements HandlerInterceptor {

    private final AuthProperties auth;

    public AuthInterceptor(AuthProperties auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;                 // CORS preflight carries no headers to check.
        }
        String path = request.getRequestURI();

        if (path.startsWith("/review")) {
            return checkAdmin(request, response);
        }
        if (path.startsWith("/interview") && auth.isCandidateSessionRequired()) {
            return checkCandidate(request, response, path);
        }
        return true;
    }

    private boolean checkAdmin(HttpServletRequest request, HttpServletResponse response) {
        String configured = auth.getAdminToken();
        if (configured == null || configured.isBlank()) {
            log.error("interviewer.auth.admin-token is not set - refusing {}. Set REVIEW_ADMIN_TOKEN.",
                    request.getRequestURI());
            return deny(response, "review console is not configured");
        }
        String presented = request.getHeader("headerAdminToken");
        if (presented == null || !constantTimeEquals(configured, presented)) {
            log.warn("rejected unauthenticated request to {}", request.getRequestURI());
            return deny(response, "not authorised");
        }
        return true;
    }

    /**
     * Only the candidate whose interview it is may start one.
     *
     * <p>Applies to the entry points that name a candidate. {@code /poll} and {@code /answer} name
     * a session id instead, which is a 128-bit random value and functions as a capability — you
     * cannot act on a session you were never told about.
     */
    private boolean checkCandidate(HttpServletRequest request, HttpServletResponse response,
                                   String path) {
        if (!path.endsWith("/stream") && !path.endsWith("/start") && !path.endsWith("/mode")) {
            return true;
        }
        String[] parts = path.split("/");
        if (parts.length < 3) {
            return true;
        }
        String candidateId = parts[2];
        String token = redis.get(REDIS_USER_TOKEN + ":" + candidateId);
        String info = redis.get(REDIS_USER_INFO + ":" + candidateId);
        if (token == null || token.isBlank() || info == null || info.isBlank()) {
            log.info("no live session for candidate {}, refusing {}", candidateId, path);
            return deny(response, "please log in again");
        }
        String presented = request.getHeader("headerUserToken");
        if (presented == null || !constantTimeEquals(token, presented)) {
            log.info("token mismatch for candidate {} on {}", candidateId, path);
            return deny(response, "please log in again");
        }
        return true;
    }

    private boolean deny(HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(
                    "{\"status\":401,\"msg\":\"" + message + "\",\"success\":false,\"data\":null}");
        } catch (Exception e) {
            log.debug("could not write the denial body: {}", e.getMessage());
        }
        return false;
    }

    /** Length-independent comparison, so a wrong token leaks nothing about how wrong it is. */
    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
