package app.morrow.recovery;

import java.util.Locale;
import java.util.regex.Pattern;

public record RecoveryActionDescriptor(RecoveryAttempt.Action action, int durationSeconds) {
    private static final Pattern MINUTES = Pattern.compile("(\\d{1,2})\\s*분");

    public static RecoveryActionDescriptor fromTitle(String title) {
        var value = title == null ? "" : title.toLowerCase(Locale.KOREAN);
        var action = actionFrom(value);
        var matcher = MINUTES.matcher(value);
        var duration = matcher.find()
                ? Math.max(1, Math.min(30, Integer.parseInt(matcher.group(1)))) * 60
                : defaultDuration(action);
        return new RecoveryActionDescriptor(action, duration);
    }

    public static int defaultDuration(RecoveryAttempt.Action action) {
        return switch (action) {
            case BREATH, SCREEN_BREAK -> 60;
            case WALK, FOCUS -> 300;
            case WATER_WALK, STRETCH -> 180;
        };
    }

    private static RecoveryAttempt.Action actionFrom(String value) {
        if (containsAny(value, "활동을 멈추", "잠시 멈추", "편한 자세에서 상태", "화면", "눈을 쉬")) {
            return RecoveryAttempt.Action.SCREEN_BREAK;
        }
        if (value.contains("물")) return RecoveryAttempt.Action.WATER_WALK;
        if (containsAny(value, "걷", "걸어", "산책", "움직", "좋은 흐름")) return RecoveryAttempt.Action.WALK;
        if (containsAny(value, "집중", "할 일", "시작해")) return RecoveryAttempt.Action.FOCUS;
        if (containsAny(value, "스트레칭", "어깨", "자세", "몸을 천천히")) return RecoveryAttempt.Action.STRETCH;
        if (containsAny(value, "호흡", "숨", "들이쉬", "내쉬")) return RecoveryAttempt.Action.BREATH;
        return RecoveryAttempt.Action.BREATH;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (var candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }
}
