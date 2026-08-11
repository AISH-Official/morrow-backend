package app.morrow.auth;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AccountPasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        var salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        var derived = derive(password, salt, ITERATIONS);
        return String.join("$", PREFIX, Integer.toString(ITERATIONS), encode(salt), encode(derived));
    }

    public boolean matches(String password, String encoded) {
        if (encoded == null) return false;
        try {
            var parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;
            var iterations = Integer.parseInt(parts[1]);
            var salt = Base64.getDecoder().decode(parts[2]);
            var expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        var spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception error) {
            throw new IllegalStateException("비밀번호를 안전하게 처리하지 못했습니다.", error);
        } finally {
            spec.clearPassword();
        }
    }

    private String encode(byte[] value) { return Base64.getEncoder().encodeToString(value); }
}
