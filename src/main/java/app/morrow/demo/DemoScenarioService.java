package app.morrow.demo;

import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInService;
import app.morrow.health.HealthSignalSnapshot;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.personalization.PersonalizationService;
import app.morrow.personalization.UserMemory;
import app.morrow.notification.PushNotificationService;
import app.morrow.privacy.DataPrivacyService;
import app.morrow.recovery.RecoveryAttempt;
import app.morrow.recovery.RecoveryAttemptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional
public class DemoScenarioService {
    private final DataPrivacyService privacy;
    private final HealthSignalSnapshotRepository healthSnapshots;
    private final CheckInService checkIns;
    private final RecoveryAttemptService recoveryAttempts;
    private final PersonalizationService personalization;
    private final PushNotificationService notifications;
    private final String configuredDemoUserId;
    private final ZoneId timeZone;

    public DemoScenarioService(
            DataPrivacyService privacy,
            HealthSignalSnapshotRepository healthSnapshots,
            CheckInService checkIns,
            RecoveryAttemptService recoveryAttempts,
            PersonalizationService personalization,
            PushNotificationService notifications,
            @Value("${morrow.demo-login.user-id:hackathon-demo}") String configuredDemoUserId,
            @Value("${morrow.time-zone:Asia/Seoul}") String timeZone
    ) {
        this.privacy = privacy;
        this.healthSnapshots = healthSnapshots;
        this.checkIns = checkIns;
        this.recoveryAttempts = recoveryAttempts;
        this.personalization = personalization;
        this.notifications = notifications;
        this.configuredDemoUserId = configuredDemoUserId;
        this.timeZone = ZoneId.of(timeZone);
    }

    public DemoResult apply(String userId, Scenario scenario) {
        if (!userId.equals(configuredDemoUserId) && !userId.startsWith("demo-")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "데모 계정에서만 시나리오를 적용할 수 있습니다.");
        }

        privacy.resetWellnessForDemo(userId);
        var now = OffsetDateTime.now(timeZone);
        var runId = java.util.UUID.randomUUID().toString().replace("-", "");
        seedHealth(userId, scenario, now, runId);
        seedCheckIns(userId, scenario, now, runId);
        seedRecoveryOutcomes(userId, scenario);
        personalization.createDeclaredMemory(userId, UserMemory.Type.GOAL, "주요 회복 상황: " + scenario.context);
        healthSnapshots.findFirstByUserIdOrderByRecordedAtDesc(userId).ifPresent(snapshot ->
                notifications.sendActionableRecoveryAlert(snapshot, 72, scenario.pushReason, "HIGH"));

        return new DemoResult(scenario.name(), scenario.title, scenario.summary, now);
    }

    private void seedHealth(String userId, Scenario scenario, OffsetDateTime now, String runId) {
        for (int day = 6; day >= 0; day--) {
            var latest = day == 0;
            var sleep = switch (scenario) {
                case SHORT_SLEEP -> latest ? 248 : 315 + day * 8;
                case SEDENTARY -> 392 + day * 3;
                case TENSION -> 390;
            };
            var heartRate = scenario == Scenario.TENSION && latest ? 92d : 70d + (day % 3);
            var resting = scenario == Scenario.TENSION && latest ? 82d : 62d + (day % 2);
            var hrv = scenario == Scenario.TENSION && latest ? 24d : 43d - day;
            var steps = scenario == Scenario.SEDENTARY && latest ? 1160d : 5200d + day * 310;
            var exercise = scenario == Scenario.SEDENTARY && latest ? 2d : 18d + day;
            healthSnapshots.save(new HealthSignalSnapshot(
                    userId,
                    "demo-" + runId + "-" + scenario.name().toLowerCase() + "-" + day,
                    HealthSignalSnapshot.Source.IPHONE,
                    sleep,
                    heartRate,
                    resting,
                    hrv,
                    steps,
                    220d + day * 8,
                    exercise,
                    steps * 0.72,
                    day % 3d,
                    15.5d,
                    98d,
                    now.minusDays(day).withHour(8).withMinute(10)
            ));
        }
    }

    private void seedCheckIns(String userId, Scenario scenario, OffsetDateTime now, String runId) {
        var cause = switch (scenario) {
            case SHORT_SLEEP -> CheckIn.Cause.SLEEP;
            case SEDENTARY -> CheckIn.Cause.WORK;
            case TENSION -> CheckIn.Cause.WORK;
        };
        var status = switch (scenario) {
            case SHORT_SLEEP -> CheckIn.Status.TIRED;
            case SEDENTARY -> CheckIn.Status.LOW_FOCUS;
            case TENSION -> CheckIn.Status.TENSE;
        };
        var notes = switch (scenario) {
            case SHORT_SLEEP -> List.of("잠을 설쳐 오전부터 무거워요", "오후에 눈이 자꾸 감겨요", "집중이 평소보다 어렵네요");
            case SEDENTARY -> List.of("회의가 이어져 오래 앉아 있었어요", "오후부터 집중이 흐려져요", "몸이 굳고 머리가 답답해요");
            case TENSION -> List.of("발표 생각에 어깨가 굳어요", "회의 직전 심장이 빨라진 느낌이에요", "짧게 진정하고 싶어요");
        };
        for (int index = 0; index < notes.size(); index++) {
            checkIns.create(new CheckInService.CreateCheckIn(
                    userId,
                    "demo-current-" + runId + "-" + scenario.name().toLowerCase() + "-" + index,
                    status,
                    cause,
                    notes.get(index),
                    CheckIn.Source.WEB,
                    now.minusDays(4L - index * 2L).withHour(14 + index).withMinute(20)
            ));
        }
        for (int index = 0; index < 3; index++) {
            checkIns.create(new CheckInService.CreateCheckIn(
                    userId,
                    "demo-previous-" + runId + "-" + scenario.name().toLowerCase() + "-" + index,
                    index == 0 ? CheckIn.Status.OK : status,
                    cause,
                    "이전 주 비교용 데모 기록",
                    CheckIn.Source.WEB,
                    now.minusDays(10L + index).withHour(15).withMinute(10)
            ));
        }
    }

    private void seedRecoveryOutcomes(String userId, Scenario scenario) {
        var preferred = switch (scenario) {
            case SHORT_SLEEP -> RecoveryAttempt.Action.WATER_WALK;
            case SEDENTARY -> RecoveryAttempt.Action.STRETCH;
            case TENSION -> RecoveryAttempt.Action.BREATH;
        };
        var reason = switch (scenario) {
            case SHORT_SLEEP -> "수면이 평소보다 짧고 피로 체크인이 반복됐어요.";
            case SEDENTARY -> "걸음과 운동 시간이 낮고 집중 저하가 기록됐어요.";
            case TENSION -> "심박 흐름과 긴장 체크인이 함께 높아졌어요.";
        };
        for (int index = 0; index < 3; index++) {
            var attempt = recoveryAttempts.createAndStart(userId, preferred, "DEMO_PATTERN", reason, "HIGH", RecoveryAttempt.Source.DEMO);
            recoveryAttempts.complete(attempt.getId(), userId, RecoveryAttempt.Outcome.IMPROVED);
        }
        var comparison = preferred == RecoveryAttempt.Action.BREATH ? RecoveryAttempt.Action.STRETCH : RecoveryAttempt.Action.BREATH;
        var attempt = recoveryAttempts.createAndStart(userId, comparison, "DEMO_COMPARISON", "다른 행동과 효과를 비교했어요.", "MEDIUM", RecoveryAttempt.Source.DEMO);
        recoveryAttempts.complete(attempt.getId(), userId, RecoveryAttempt.Outcome.SAME);
    }

    public enum Scenario {
        SHORT_SLEEP("수면이 부족한 아침", "짧은 수면 뒤 오전 피로", "수면 감소를 감지하고 물과 짧은 걷기를 우선 제안합니다.", "수면이 평소보다 짧고 피로 체크인이 반복됐어요."),
        SEDENTARY("오래 앉아 있을 때", "오래 앉은 오후의 집중 저하", "낮은 활동량과 집중 저하를 묶어 스트레칭을 제안합니다.", "걸음과 운동 시간이 낮고 집중 저하가 기록됐어요."),
        TENSION("발표·회의 전 긴장", "발표 전 긴장 상승", "높아진 긴장 신호를 설명하고 1분 호흡을 바로 실행합니다.", "심박 흐름과 긴장 체크인이 함께 높아졌어요.");

        private final String context;
        private final String title;
        private final String summary;
        private final String pushReason;

        Scenario(String context, String title, String summary, String pushReason) {
            this.context = context;
            this.title = title;
            this.summary = summary;
            this.pushReason = pushReason;
        }
    }

    public record DemoResult(String scenario, String title, String summary, OffsetDateTime generatedAt) {}
}
