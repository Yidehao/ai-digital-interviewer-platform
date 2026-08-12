package org.interviewer.speech;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.interviewer.utils.GoogleSpeechConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Owns the single {@link SpeechClient} for the application.
 *
 * Previously every call to /speech/uploadVoice constructed a client and re-read the
 * service-account JSON from disk, which meant a file read, a credential parse and a fresh gRPC
 * channel (including TLS handshake) on the latency path of every answer a candidate gave.
 *
 * The client is built lazily rather than at startup on purpose: credentials are environment
 * configuration, and a machine without them should still be able to boot and run every part of
 * the app that does not transcribe audio.
 */
@Component
public class GoogleSpeechClientProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleSpeechClientProvider.class);

    @Resource
    private GoogleSpeechConfig googleSpeechConfig;

    private volatile SpeechClient client;

    /**
     * The shared client, constructed on first use.
     *
     * @throws IllegalStateException if no credentials are configured
     */
    public SpeechClient get() throws Exception {
        SpeechClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = create();
                    client = local;
                }
            }
        }
        return local;
    }

    private SpeechClient create() throws Exception {
        String path = resolveCredentialsPath();
        log.info("Initialising Google Speech client from credentials at {}", path);
        try (InputStream keyStream = new FileInputStream(path)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(keyStream);
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
            return SpeechClient.create(settings);
        }
    }

    /** Absolute path of the credentials JSON: environment variable first, then config. */
    private String resolveCredentialsPath() {
        String fromEnv = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (StringUtils.isNotBlank(fromEnv)) {
            return fromEnv.trim();
        }
        String fromConfig = googleSpeechConfig.getCredentials().getLocation();
        if (StringUtils.isNotBlank(fromConfig)) {
            return fromConfig.replaceFirst("^file:", "").trim();
        }
        throw new IllegalStateException(
                "Google credentials are not configured. Choose one of:\n" +
                "1) export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/key.json\n" +
                "2) set google.cloud.credentials.location in application-dev.yml"
        );
    }

    @PreDestroy
    public void close() {
        SpeechClient local = client;
        if (local != null) {
            local.close();
        }
    }
}
