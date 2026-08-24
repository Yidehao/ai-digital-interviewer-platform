package org.interviewer.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.UUID;

/**
 * A stable name for this process, so a session can record which node owns it.
 *
 * <p>Needed because two things in this system are unavoidably node-local: the SSE emitter, which is
 * a TCP socket, and the live session object, which is what the interview loop is actually mutating.
 * Neither can be moved to Redis — an object in Redis is a snapshot, and a socket cannot be
 * serialised at all. What can be shared is the <em>knowledge of where they are</em>, which is what
 * this identity is for.
 *
 * <p>Host plus PID plus a random suffix. Host alone collides when two instances share a machine,
 * which is exactly how this gets tested; PID alone collides across hosts; and the random suffix
 * means a restarted process does not inherit the dead one's routing.
 */
@Slf4j
@Component
public class NodeIdentity {

    private String id;

    @PostConstruct
    void resolve() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            // A node that cannot name itself still needs a name. Losing the hostname costs
            // legibility in logs, not correctness.
            host = "unknown-host";
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        id = host + "-" + pid + "-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("node identity {}", id);
    }

    public String id() {
        return id;
    }
}
