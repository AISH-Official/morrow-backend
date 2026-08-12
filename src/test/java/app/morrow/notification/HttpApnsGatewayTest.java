package app.morrow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpApnsGatewayTest {
    @Test
    void placesNavigationMetadataAtPayloadRootForAppleDelegates() {
        var payload = new HttpApnsGateway(new ApnsProperties(), new ObjectMapper()).buildPayload(
                "체크인",
                "지금 상태를 확인해요.",
                "MORROW_CHECKIN",
                Map.of("type", "CHECKIN", "source", "HOURLY_REMINDER", "aps", "must-not-overwrite")
        );

        assertThat(payload).containsEntry("type", "CHECKIN").containsEntry("source", "HOURLY_REMINDER");
        assertThat(payload).doesNotContainKey("morrow");
        assertThat(payload.get("aps")).isInstanceOf(Map.class);
    }
}
