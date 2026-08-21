package org.interviewer.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stale-URL class of bug, made unrepresentable.
 *
 * <p>Two questions in the live bank point at {@code http://192.168.0.104:9010} and have been dead
 * links since the router reissued that lease. Twelve more point at {@code 127.0.0.1}, which works
 * on the machine that wrote them and fails on every phone. Neither was a coding mistake — both are
 * what happens when a value that belongs to the deployment gets persisted as if it belonged to the
 * object.
 */
class MediaUrlResolverTest {

    private final MediaUrlResolver resolver = new MediaUrlResolver("http://minio.internal:9010");

    @Test
    @DisplayName("a stored path becomes a URL for the configured endpoint")
    void pathBecomesUrl() {
        assertThat(resolver.resolve("/interviewer/clip.mp4"))
                .isEqualTo("http://minio.internal:9010/interviewer/clip.mp4");
    }

    @Test
    @DisplayName("a legacy absolute URL is rewritten, not trusted")
    void legacyAbsoluteUrlIsRehosted() {
        // The 192.168 rows in the live bank. Passing these through unchanged would keep serving a
        // LAN address that has not existed for months.
        assertThat(resolver.resolve("http://192.168.0.104:9010/interviewer/clip.mp4"))
                .isEqualTo("http://minio.internal:9010/interviewer/clip.mp4");
        assertThat(resolver.resolve("http://127.0.0.1:9010/interviewer/clip.mp4"))
                .isEqualTo("http://minio.internal:9010/interviewer/clip.mp4");
    }

    @Test
    @DisplayName("resolving twice is the same as resolving once")
    void resolutionIsIdempotent() {
        // The admin UI submits back the URL it was shown. Without idempotence the endpoint would
        // accumulate: http://minio.internal:9010/http://127.0.0.1:9010/...
        String once = resolver.resolve("/interviewer/clip.mp4");
        assertThat(resolver.resolve(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("null and blank pass through rather than becoming a bare endpoint")
    void emptyStaysEmpty() {
        // A question with no avatar clip must not turn into a link to the bucket root, which would
        // render as a broken player instead of no player.
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve("")).isEmpty();
        assertThat(resolver.resolve("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("a malformed value degrades to a path instead of throwing")
    void malformedDegradesGracefully() {
        // Throwing here would take out a whole interview over one bad row.
        assertThat(resolver.pathOf("not a url at all")).isEqualTo("not a url at all");
        assertThat(resolver.pathOf("http://host-with-no-path")).isEmpty();
    }

    @Test
    @DisplayName("a trailing slash on the endpoint does not double up")
    void trailingSlashIsTolerated() {
        assertThat(new MediaUrlResolver("http://minio.internal:9010/").resolve("/a/b.mp4"))
                .isEqualTo("http://minio.internal:9010/a/b.mp4");
    }
}
