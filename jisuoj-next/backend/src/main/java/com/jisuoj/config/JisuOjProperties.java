package com.jisuoj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "jisuoj")
public class JisuOjProperties {
    private Path dataDir = Path.of("/home/judge/data");
    private final Auth auth = new Auth();
    private final Judge judge = new Judge();

    public Path getDataDir() {
        return dataDir;
    }

    public void setDataDir(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Auth getAuth() {
        return auth;
    }

    public Judge getJudge() {
        return judge;
    }

    public static class Auth {
        private String secret = "change-this-development-secret-before-production-please";
        private Duration tokenTtl = Duration.ofHours(8);
        private boolean secureCookie = false;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }

        public boolean isSecureCookie() {
            return secureCookie;
        }

        public void setSecureCookie(boolean secureCookie) {
            this.secureCookie = secureCookie;
        }
    }

    public static class Judge {
        private boolean notificationEnabled = true;
        private String host = "judge-agent";
        private int port = 1536;
        private String payload = "../judge";
        private int submissionCooldownSeconds = 5;

        public boolean isNotificationEnabled() {
            return notificationEnabled;
        }

        public void setNotificationEnabled(boolean notificationEnabled) {
            this.notificationEnabled = notificationEnabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public int getSubmissionCooldownSeconds() {
            return submissionCooldownSeconds;
        }

        public void setSubmissionCooldownSeconds(int submissionCooldownSeconds) {
            this.submissionCooldownSeconds = submissionCooldownSeconds;
        }
    }

}
