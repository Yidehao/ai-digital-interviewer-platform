package org.interviewer.aspect;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.interviewer.utils.MdcKeys;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stamps every request with a correlation id, and every interview request with its session id.
 *
 * <p>Runs first so the id exists before anything else logs. The id is also returned as
 * {@code X-Request-Id}, which is what makes a user-reported problem findable: "it failed and the
 * response header said abc123" turns a search through timestamps into one grep.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class RequestMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        org.slf4j.MDC.put(MdcKeys.REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);

        // /interview/{id}/... - the id is the candidate on the stream endpoint and the session on
        // the answer endpoint. Both are worth having; which one it is is clear from the path.
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/interview/")) {
            String[] parts = path.split("/");
            if (parts.length > 2) {
                org.slf4j.MDC.put(MdcKeys.SESSION_ID, parts[2]);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat reuses threads. Not clearing would attach this request's identity to whatever
            // runs next on the same thread.
            MdcKeys.clear();
        }
    }
}
