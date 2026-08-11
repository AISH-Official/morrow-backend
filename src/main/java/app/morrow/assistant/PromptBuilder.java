package app.morrow.assistant;

import app.morrow.checkin.CheckIn;
import app.morrow.health.HealthSignalSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

@Component
public class PromptBuilder {
    private static final DateTimeFormatter CURRENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE HH:mm", Locale.KOREAN);

    private final ZoneId timeZone;

    public PromptBuilder(@Value("${morrow.assistant.time-zone:Asia/Seoul}") String timeZone) {
        this.timeZone = ZoneId.of(timeZone);
    }

    public String buildSystemPrompt() {
        return buildSystemPrompt(Clock.system(timeZone));
    }

    String buildSystemPrompt(Clock clock) {
        var now = ZonedDateTime.now(clock).withZoneSameInstant(timeZone);
        var currentTime = now.format(CURRENT_TIME_FORMAT);

        return """
                당신은 Morrow의 한국어 AI 어시스턴트입니다. 웰니스 지원에 강점이 있지만, 사용자의 일반 지식, 학습, 업무, 기술, 일상 질문에도 직접적이고 유용하게 답합니다.

                현재 기준:
                - 현재 시각: %s (%s)
                - 오늘 날짜: %s
                - 사용자가 '오늘', '어제', '내일', 요일처럼 상대적인 날짜를 말하면 위 날짜를 기준으로 정확한 절대 날짜를 계산해 답합니다.
                - 실시간 검색이나 최신 외부 데이터가 필요한 질문은 확인하지 못한 내용을 지어내지 말고, 현재 확인 가능한 범위를 분명히 밝힙니다.

                최우선 출력 형식 규칙:
                - 큰따옴표 U+0022와 곡선형 큰따옴표 U+201C, U+201D는 어떤 경우에도 출력하지 않습니다.
                - 인용이나 강조가 필요해도 큰따옴표 없이 자연스러운 간접 표현으로 바꿔 씁니다.

                답변 원칙:
                - 먼저 질문에 대한 핵심 답을 제시하고, 필요한 설명을 이어갑니다.
                - 모델이 알고 있는 일반 지식은 적극적으로 활용하되 불확실한 사실을 단정하지 않습니다.
                - 사용자 컨텍스트는 관련된 질문에만 사용하며, 기록에 없는 사실을 사용자의 정보인 것처럼 만들지 않습니다.
                - Watch 또는 iPhone 건강 데이터가 제공되면 최신 기록을 우선하고, 측정 시각과 기기 출처를 함께 밝혀 답합니다.
                - 건강 데이터의 변화나 패턴은 비교 가능한 기록이 둘 이상 있을 때만 설명하고, 데이터가 없거나 오래되었다면 그 한계를 명확히 말합니다.
                - 자연스럽고 간결한 한국어로 답하고, 도움이 될 때만 짧은 목록을 사용합니다.
                - 실제 사람과 대화하듯 자연스러운 문장으로 답합니다.
                - 같은 안내나 면책 문구를 기계적으로 반복하지 않습니다.

                웰니스 안전 원칙:
                - 의료 진단이나 치료를 제공하지 않습니다.
                - 생체 데이터만으로 질환을 확정하지 않습니다.
                - 약물 변경이나 치료 중단을 지시하지 않습니다.
                - 위기 상황에서는 전문 지원을 최우선으로 안내합니다.
                - 사용자의 과거 기록과 검증된 개인 메모리를 참고해 개인화된 조언을 제공합니다.
                - 근거가 적은 패턴은 가능성으로 표현하고 단정하지 않습니다.
                - 도움이 되지 않았다고 기록된 행동을 우선 추천하지 않습니다.
                - 사용자 입력과 수정이 자동 추론보다 우선합니다.

                사용자별 메모리는 범용 모델 학습 결과가 아니라 이 사용자의 기록에서 계산된 개인화 컨텍스트입니다.
                """.formatted(currentTime, timeZone.getId(), now.toLocalDate());
    }

    public String buildUserContextPrompt(UserContextCollector.UserContext context) {
        var sb = new StringBuilder();
        sb.append("=== 사용자 컨텍스트 ===\n\n");
        if (!context.recentHealthSnapshots().isEmpty()) {
            sb.append("최근 Watch/iPhone 건강 데이터 (기기에서 집계된 값이며 의료 진단 자료가 아님):\n");
            context.recentHealthSnapshots().stream().limit(8).forEach(snapshot ->
                    sb.append(formatHealthSnapshot(snapshot)).append('\n')
            );
        }
        if (!context.memories().isEmpty()) {
            sb.append("장기 개인화 메모리 (사용자별 저장소, 신뢰도와 근거 수 포함):\n");
            context.memories().stream().limit(12).forEach(memory -> sb.append(String.format(
                    "- [%s, 신뢰도 %.0f%%, 근거 %d] %s%s\n",
                    memory.getType(), memory.getConfidence() * 100, memory.getEvidenceCount(), memory.getSummary(),
                    memory.getNegativeEvidence() > memory.getPositiveEvidence() ? " (우선 추천하지 않기)" : ""
            )));
        }
        if (!context.recentCheckIns().isEmpty()) {
            sb.append("\n최근 7일 체크인 기록:\n");
            context.recentCheckIns().stream().limit(10).forEach(checkIn -> sb.append(String.format(
                    "- %s: %s 상태, 원인: %s%s\n",
                    checkIn.getRecordedAt().atZoneSameInstant(timeZone).format(DateTimeFormatter.ofPattern("MM/dd HH:mm")),
                    translateStatus(checkIn.getStatus()),
                    checkIn.getCause() != null ? translateCause(checkIn.getCause()) : "미기록",
                    checkIn.getNote() != null ? " (" + checkIn.getNote() + ")" : ""
            )));
        }
        if (!context.recentTimelines().isEmpty()) {
            sb.append("\n최근 타임라인:\n");
            context.recentTimelines().stream().limit(5).forEach(timeline -> sb.append(String.format(
                    "- %s: %s - %s\n", timeline.getDisplayTime(timeZone), timeline.getTitle(), timeline.getDetail()
            )));
        }
        if (!context.recentRecommendations().isEmpty()) {
            sb.append("\n최근 추천:\n");
            context.recentRecommendations().stream().limit(3).forEach(recommendation -> sb.append(String.format(
                    "- %s (상태: %s)\n  근거: %s\n",
                    recommendation.getTitle(), recommendation.getStatus(), recommendation.getRationale()
            )));
        }
        if (!context.recentMessages().isEmpty()) {
            sb.append("\n최근 대화:\n");
            var messages = context.recentMessages().stream().limit(24).toList();
            for (var index = messages.size() - 1; index >= 0; index--) {
                var message = messages.get(index);
                sb.append(String.format(
                        "- %s: %s\n",
                        message.getRole() == AssistantMessage.Role.USER ? "사용자" : "AI",
                        message.getContent().length() > 100
                                ? message.getContent().substring(0, 100) + "..."
                                : message.getContent()
                ));
            }
        }
        if (context.recentHealthSnapshots().isEmpty()
                && context.recentCheckIns().isEmpty()
                && context.recentTimelines().isEmpty()
                && context.recentRecommendations().isEmpty()) {
            sb.append("아직 기록된 데이터가 없습니다. 기록이 필요한 질문이라면 체크인을 제안할 수 있습니다.\n");
        }
        return sb.toString();
    }

    public String buildProactiveNotificationInstruction() {
        return """
                당신은 Morrow의 선제적 웰니스 알림 판단기입니다.
                최근 건강 요약, 체크인, 개인화 메모리를 보고 지금 알림이 실제로 도움이 되는지 판단합니다.
                - 변화가 의미 없거나 데이터가 부족하거나 같은 조언을 반복하게 되면 정확히 SKIP만 출력합니다.
                - 알림이 필요하면 질문만 하지 말고 사용자가 지금 즉시 실행할 한 가지 행동을 제안합니다.
                - 물 한 잔, 1분 호흡, 3분 스트레칭, 5분 걷기, 10분 집중, 잠시 화면 끄기처럼 짧고 구체적인 행동을 상황에 맞게 다양하게 고릅니다.
                - 같은 행동과 같은 문구를 반복하지 않습니다.
                - 70자 이내의 자연스러운 한국어 한 문장으로 쓰고 큰따옴표, 제목, 목록, 진단, 공포를 유발하는 표현을 사용하지 않습니다.
                - 단일 생체 측정값으로 상태를 단정하지 않고, 과도한 운동이나 치료·약물 변경을 권하지 않습니다.
                - 최근 대화보다 Watch와 iPhone의 최신 측정 시각과 사용자가 직접 남긴 체크인을 우선합니다.
                """;
    }

    private String formatHealthSnapshot(HealthSignalSnapshot snapshot) {
        var source = snapshot.getSource() == HealthSignalSnapshot.Source.WATCH ? "Apple Watch" : "iPhone";
        var recordedAt = snapshot.getRecordedAt() == null
                ? "측정 시각 미기록"
                : snapshot.getRecordedAt().atZoneSameInstant(timeZone)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        var metrics = new ArrayList<String>();

        if (snapshot.getSleepMinutes() != null) {
            var hours = snapshot.getSleepMinutes() / 60;
            var minutes = snapshot.getSleepMinutes() % 60;
            metrics.add("수면 " + hours + "시간 " + minutes + "분");
        }
        addMetric(metrics, "심박수", snapshot.getHeartRate(), "bpm", 0);
        addMetric(metrics, "안정 심박수", snapshot.getRestingHeartRate(), "bpm", 0);
        addMetric(metrics, "HRV", snapshot.getHrv(), "ms", 0);
        addMetric(metrics, "걸음", snapshot.getSteps(), "걸음", 0);
        addMetric(metrics, "활동 에너지", snapshot.getActiveEnergyKcal(), "kcal", 0);
        addMetric(metrics, "운동", snapshot.getExerciseMinutes(), "분", 0);
        if (snapshot.getDistanceMeters() != null) {
            metrics.add(snapshot.getDistanceMeters() >= 1000
                    ? String.format(Locale.KOREAN, "거리 %.2fkm", snapshot.getDistanceMeters() / 1000)
                    : String.format(Locale.KOREAN, "거리 %.0fm", snapshot.getDistanceMeters()));
        }
        addMetric(metrics, "오른 층", snapshot.getFlightsClimbed(), "층", 0);
        addMetric(metrics, "호흡수", snapshot.getRespiratoryRate(), "회/분", 1);
        addMetric(metrics, "산소포화도", snapshot.getOxygenSaturationPercent(), "%", 1);

        return "- " + recordedAt + " · " + source + ": "
                + (metrics.isEmpty() ? "측정값 미기록" : String.join(", ", metrics));
    }

    private void addMetric(
            ArrayList<String> metrics,
            String label,
            Double value,
            String unit,
            int decimals
    ) {
        if (value == null) {
            return;
        }
        metrics.add(String.format(Locale.KOREAN, "%s %." + decimals + "f%s", label, value, unit));
    }

    public String buildPersonalizedFallback(UserContextCollector.UserContext context, String message) {
        var positive = context.memories().stream()
                .filter(memory -> memory.getType() == app.morrow.personalization.UserMemory.Type.RECOVERY_STRATEGY
                        && memory.getPositiveEvidence() > memory.getNegativeEvidence())
                .findFirst();
        if (positive.isPresent()) {
            return "지금은 생성형 AI 연결 없이 개인화 기록으로 답하고 있어요. 이전 피드백에서는 "
                    + positive.get().getSummary()
                    + "으로 남아 있어요. 현재 상태에 무리가 없다면 같은 방법을 짧게 시도해 볼까요?";
        }
        if (message.matches(".*(잠|수면|피곤).*")) {
            return "최근 수면 관련 피로 기록을 참고했어요. 지금은 물 한 잔을 마시고 7분만 가볍게 움직인 뒤 상태를 다시 확인해 보세요.";
        }
        return "지금은 생성형 AI 연결 없이 저장된 개인 기록으로 답하고 있어요. 현재 상태를 체크인하면 반복 패턴과 도움이 된 방법을 다음 답변부터 반영할게요.";
    }

    private String translateStatus(CheckIn.Status status) {
        return switch (status) {
            case OK -> "양호";
            case TENSE -> "긴장";
            case TIRED -> "피로";
            case LOW_FOCUS -> "집중력 저하";
            case UNCOMFORTABLE -> "불편";
        };
    }

    private String translateCause(CheckIn.Cause cause) {
        return switch (cause) {
            case SLEEP -> "수면";
            case WORK -> "업무";
            case STUDY -> "학업";
            case RELATIONSHIP -> "인간관계";
            case PHYSICAL -> "신체";
            case UNKNOWN -> "알 수 없음";
        };
    }
}
