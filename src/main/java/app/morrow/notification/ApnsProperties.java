package app.morrow.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "morrow.push.apns")
public class ApnsProperties {
    private boolean enabled;
    private String teamId = "";
    private String keyId = "";
    private String privateKey = "";
    private String privateKeyPath = "";
    private String iosTopic = "com.qlsl1198.morrowwellness";
    private String watchTopic = "com.qlsl1198.morrowwellness.watchkitapp";

    public boolean isEnabled() { return enabled; } public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTeamId() { return teamId; } public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getKeyId() { return keyId; } public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getPrivateKey() { return privateKey; } public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getPrivateKeyPath() { return privateKeyPath; } public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }
    public String getIosTopic() { return iosTopic; } public void setIosTopic(String iosTopic) { this.iosTopic = iosTopic; }
    public String getWatchTopic() { return watchTopic; } public void setWatchTopic(String watchTopic) { this.watchTopic = watchTopic; }
    public boolean ready() { return enabled && !teamId.isBlank() && !keyId.isBlank() && (!privateKey.isBlank() || readablePrivateKeyPath()); }
    public String topic(PushDevice.Platform platform) { return platform == PushDevice.Platform.WATCHOS ? watchTopic : iosTopic; }
    private boolean readablePrivateKeyPath() { try { return !privateKeyPath.isBlank() && Files.isReadable(Path.of(privateKeyPath)); } catch (Exception ignored) { return false; } }
}
