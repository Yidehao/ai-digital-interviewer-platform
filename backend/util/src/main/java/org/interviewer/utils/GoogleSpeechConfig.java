package org.interviewer.utils;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google Cloud Speech-to-Text configuration.
 */
@Component
@ConfigurationProperties(prefix = "google.cloud")
public class GoogleSpeechConfig {

    private Credentials credentials = new Credentials();
    private Speech speech = new Speech();

    public static class Credentials {

        private String location = "";

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }

    public static class Speech {
        /** GCP project ID, must match project_id in the credentials JSON. */
        private String projectId = "";
        /** Recognition language code, e.g. zh-CN or en-US. */
        private String languageCode = "en-US";

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getLanguageCode() {
            return languageCode;
        }

        public void setLanguageCode(String languageCode) {
            this.languageCode = languageCode;
        }
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    public Speech getSpeech() {
        return speech;
    }

    public void setSpeech(Speech speech) {
        this.speech = speech;
    }
}
