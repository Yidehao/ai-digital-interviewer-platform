package org.interviewer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CorsConfig
 **/
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Comma-separated list of allowed origins.
     *
     * A list rather than a single value because the admin panel, the uni-app H5 dev server and
     * any SSE client each run on their own port. allowCredentials(true) forbids "*", so every
     * origin that needs in has to be named here.
     */
    @Value("${interviewer-url.frontend.domain}")
    private String domains;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] allowedOrigins = Arrays.stream(domains.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        // MCP transport endpoints, mapped first and deliberately more permissive.
        //
        // The single-origin policy below exists to protect candidate-facing endpoints that carry a
        // session token. An MCP client is not a browser on the candidate's origin: Inspector runs
        // on its own localhost port, and Claude Desktop sends no Origin header at all. Leaving
        // these under the /** mapping produces a 403 that reads like a protocol error and costs an
        // afternoon to diagnose.
        //
        // allowCredentials is false here, which is what makes the wildcard pattern legal and also
        // what makes it safe: these endpoints carry no session cookie and no candidate token.
        registry.addMapping("/sse")
                .allowedOriginPatterns("*")
                .allowedMethods("GET")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(60 * 60);

        registry.addMapping("/mcp/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(60 * 60);

        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(60 * 60);

    }
}