package org.interviewer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link AuthInterceptor}.
 *
 * <p>Paths are listed explicitly rather than intercepting everything and excluding the public ones.
 * An allow-list of protected paths is the weaker pattern in general — a new sensitive endpoint is
 * unprotected until someone remembers it — but the alternative here would silently break the
 * scripted interview flow, the SMS login and file upload, none of which have tokens to present.
 * Being explicit about which is which is better than a blanket rule with five exceptions nobody
 * can enumerate.
 */
@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcAuthConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/review/**", "/interview/**");
    }
}
