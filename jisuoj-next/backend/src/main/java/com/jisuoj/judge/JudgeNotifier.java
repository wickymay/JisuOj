package com.jisuoj.judge;

import com.jisuoj.config.JisuOjProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

@Component
public class JudgeNotifier {
    private static final Logger log = LoggerFactory.getLogger(JudgeNotifier.class);
    private final JisuOjProperties properties;

    public JudgeNotifier(JisuOjProperties properties) {
        this.properties = properties;
    }

    public void notifySubmission(int solutionId) {
        JisuOjProperties.Judge judge = properties.getJudge();
        if (!judge.isNotificationEnabled()) {
            return;
        }
        String payloadValue = judge.getPayload().isBlank() ? String.valueOf(solutionId) : judge.getPayload();
        byte[] payload = payloadValue.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(
                    payload, payload.length, InetAddress.getByName(judge.getHost()), judge.getPort());
            socket.send(packet);
        } catch (Exception ex) {
            // judged also polls the database, so a lost UDP wake-up does not lose the submission.
            log.warn("Judge notification failed for solution {}: {}", solutionId, ex.getMessage());
        }
    }
}
