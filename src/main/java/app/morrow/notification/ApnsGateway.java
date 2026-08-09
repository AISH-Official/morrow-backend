package app.morrow.notification;

public interface ApnsGateway {
    SendResult send(PushDevice device, String title, String body, String category, java.util.Map<String, Object> data);
    record SendResult(boolean accepted, int statusCode, String reason) {}
}
