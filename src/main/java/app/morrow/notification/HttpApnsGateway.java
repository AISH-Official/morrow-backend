package app.morrow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HttpApnsGateway implements ApnsGateway {
    private final ApnsProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String cachedJwt;
    private volatile long cachedAt;

    public HttpApnsGateway(ApnsProperties properties, ObjectMapper mapper) { this.properties = properties; this.mapper = mapper; }

    @Override public SendResult send(PushDevice device, String title, String body, String category, Map<String, Object> data) {
        if (!properties.ready()) return new SendResult(false, 503, "APNsNotConfigured");
        try {
            var aps = new LinkedHashMap<String, Object>();
            aps.put("alert", Map.of("title", title, "body", body)); aps.put("sound", "default"); aps.put("category", category);
            var payload = new LinkedHashMap<String, Object>(); payload.put("aps", aps); payload.put("morrow", data);
            var host = device.getEnvironment() == PushDevice.Environment.PRODUCTION ? "api.push.apple.com" : "api.sandbox.push.apple.com";
            var request = HttpRequest.newBuilder(URI.create("https://" + host + "/3/device/" + device.getDeviceToken()))
                    .timeout(Duration.ofSeconds(15)).header("authorization", "bearer " + jwt()).header("apns-topic", properties.topic(device.getPlatform()))
                    .header("apns-push-type", "alert").header("apns-priority", "10").header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var accepted = response.statusCode() == 200;
            var reason = accepted ? "Accepted" : parseReason(response.body());
            return new SendResult(accepted, response.statusCode(), reason);
        } catch (Exception error) { return new SendResult(false, 502, error.getClass().getSimpleName()); }
    }

    private synchronized String jwt() throws Exception {
        var now = Instant.now().getEpochSecond();
        if (cachedJwt != null && now - cachedAt < 45 * 60) return cachedJwt;
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var header = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "ES256", "kid", properties.getKeyId())));
        var claims = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("iss", properties.getTeamId(), "iat", now)));
        var input = header + "." + claims;
        var signer = Signature.getInstance("SHA256withECDSA"); signer.initSign(loadKey()); signer.update(input.getBytes(StandardCharsets.US_ASCII));
        cachedJwt = input + "." + encoder.encodeToString(derToJose(signer.sign(), 32)); cachedAt = now;
        return cachedJwt;
    }

    private ECPrivateKey loadKey() throws Exception {
        var pem = properties.getPrivateKey();
        if (pem.isBlank()) pem = Files.readString(Path.of(properties.getPrivateKeyPath()));
        var encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
    }

    static byte[] derToJose(byte[] der, int partLength) {
        if (der.length < 8 || der[0] != 0x30) throw new IllegalArgumentException("Invalid ECDSA signature");
        var index = 2; if ((der[1] & 0x80) != 0) index = 2 + (der[1] & 0x7f);
        if (der[index++] != 0x02) throw new IllegalArgumentException("Invalid ECDSA R value");
        var rLength = der[index++] & 0xff; var r = new byte[rLength]; System.arraycopy(der, index, r, 0, rLength); index += rLength;
        if (der[index++] != 0x02) throw new IllegalArgumentException("Invalid ECDSA S value");
        var sLength = der[index++] & 0xff; var s = new byte[sLength]; System.arraycopy(der, index, s, 0, sLength);
        var output = new byte[partLength * 2]; copyUnsigned(r, output, 0, partLength); copyUnsigned(s, output, partLength, partLength); return output;
    }

    private static void copyUnsigned(byte[] value, byte[] output, int offset, int length) {
        var source = value.length > length && value[0] == 0 ? 1 : 0; var count = Math.min(value.length - source, length);
        System.arraycopy(value, value.length - count, output, offset + length - count, count);
    }

    private String parseReason(String body) { try { return mapper.readTree(body).path("reason").asText("Rejected"); } catch (Exception ignored) { return "Rejected"; } }
}
