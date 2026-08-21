package org.interviewer.base;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns a stored object path into a URL the current deployment can actually fetch.
 *
 * <p>{@code question_lib.ai_src} used to hold a fully-qualified URL, written once at upload time
 * with whatever address the machine happened to have. Two of the original questions still point at
 * {@code http://192.168.0.104:9010} — a LAN address this machine has not held for months, so those
 * avatar clips are dead links, and they became dead the moment the router handed out a different
 * lease. The later twelve point at {@code 127.0.0.1}, which works on the desktop that wrote them
 * and fails on every phone that loads the interview page.
 *
 * <p>The fix is to store <em>where the object is</em> and decide <em>how to reach it</em> at read
 * time, because the first fact is permanent and the second is deployment configuration that
 * changes without anyone editing a row.
 *
 * <p>Absolute URLs already in the database are rewritten here rather than trusted: V4 normalises
 * the rows, but a client that still POSTs an absolute URL would otherwise reintroduce them one at
 * a time. Anything already relative passes through untouched.
 */
@Component
public class MediaUrlResolver {

    private final String endpoint;

    public MediaUrlResolver(@Value("${minio.endpoint:}") String endpoint) {
        this.endpoint = endpoint == null ? "" : endpoint.replaceAll("/+$", "");
    }

    /** @return an absolute URL for {@code stored}, or {@code stored} unchanged if it is blank */
    public String resolve(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        return endpoint + "/" + pathOf(stored).replaceFirst("^/+", "");
    }

    /**
     * The bucket-and-object part, with any scheme and authority discarded.
     *
     * <p>Deliberately not {@code new URI(...)}: a malformed legacy value should degrade to "treat
     * it as a path" rather than throw on a question that would otherwise have been servable.
     */
    public String pathOf(String stored) {
        if (stored == null) {
            return null;
        }
        int scheme = stored.indexOf("://");
        if (scheme < 0) {
            return stored;
        }
        int slash = stored.indexOf('/', scheme + 3);
        return slash < 0 ? "" : stored.substring(slash);
    }
}
