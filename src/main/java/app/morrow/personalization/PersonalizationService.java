package app.morrow.personalization;

import app.morrow.checkin.CheckIn;
import app.morrow.checkin.CheckInRepository;
import app.morrow.recommendation.Recommendation;
import app.morrow.recommendation.RecommendationFeedbackRepository;
import app.morrow.recommendation.RecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service @Transactional
public class PersonalizationService {
    private final UserMemoryRepository memories;
    private final CheckInRepository checkIns;
    private final RecommendationRepository recommendations;
    private final RecommendationFeedbackRepository feedback;

    public PersonalizationService(UserMemoryRepository memories, CheckInRepository checkIns, RecommendationRepository recommendations, RecommendationFeedbackRepository feedback) {
        this.memories = memories;
        this.checkIns = checkIns;
        this.recommendations = recommendations;
        this.feedback = feedback;
    }

    public void learnFromCheckIn(CheckIn checkIn) {
        if (checkIn.getCause() == null) return;
        var key = "trigger:" + checkIn.getCause().name().toLowerCase(Locale.ROOT) + ":" + checkIn.getStatus().name().toLowerCase(Locale.ROOT);
        var summary = causeLabel(checkIn.getCause()) + " 맥락에서 " + statusLabel(checkIn.getStatus()) + " 상태가 기록됨";
        observe(checkIn.getUserId(), UserMemory.Type.TRIGGER_PATTERN, key, summary, true, UserMemory.Source.CHECK_IN_PATTERN);
    }

    public void learnFromRecommendation(Recommendation recommendation, boolean helpful) {
        var key = strategyKey(recommendation.getTitle());
        var result = helpful ? "도움이 됐음" : "도움이 되지 않았음";
        var summary = "‘" + recommendation.getTitle() + "’ 회복 행동이 " + result;
        observe(recommendation.getUserId(), UserMemory.Type.RECOVERY_STRATEGY, key, summary, helpful, UserMemory.Source.RECOMMENDATION_FEEDBACK);
    }

    public UserMemory createDeclaredMemory(String userId, UserMemory.Type type, String summary) {
        if (type == UserMemory.Type.TRIGGER_PATTERN || type == UserMemory.Type.RECOVERY_STRATEGY) {
            throw new IllegalArgumentException("직접 입력 메모리는 PREFERENCE 또는 GOAL만 사용할 수 있습니다.");
        }
        var key = "declared:" + UUID.randomUUID();
        var memory = new UserMemory(normalizeUserId(userId), type, key, summary, UserMemory.Source.USER_DECLARED);
        memory.observe(summary, true, UserMemory.Source.USER_DECLARED);
        return memories.save(memory);
    }

    public UserMemory updateMemory(UUID id, String userId, String summary, Boolean active) {
        var memory = findOwned(id, userId);
        memory.correct(summary, active);
        return memory;
    }

    public void deleteMemory(UUID id, String userId) { memories.delete(findOwned(id, userId)); }

    @Transactional(readOnly = true)
    public List<UserMemory> activeMemories(String userId) {
        return memories.findByUserIdAndActiveTrueOrderByConfidenceDescUpdatedAtDesc(normalizeUserId(userId));
    }

    @Transactional(readOnly = true)
    public Profile profile(String userId) {
        var normalized = normalizeUserId(userId);
        var active = activeMemories(normalized);
        var positiveStrategies = active.stream().filter(m -> m.getType() == UserMemory.Type.RECOVERY_STRATEGY && m.getPositiveEvidence() > m.getNegativeEvidence()).count();
        var avoidStrategies = active.stream().filter(m -> m.getType() == UserMemory.Type.RECOVERY_STRATEGY && m.getNegativeEvidence() >= m.getPositiveEvidence()).count();
        var evidence = active.stream().mapToInt(UserMemory::getEvidenceCount).sum();
        var lastLearnedAt = active.stream().map(UserMemory::getUpdatedAt).max(OffsetDateTime::compareTo).orElse(null);
        return new Profile(normalized, active.size(), evidence, positiveStrategies, avoidStrategies, lastLearnedAt, !active.isEmpty());
    }

    public Profile rebuild(String userId) {
        var normalized = normalizeUserId(userId);
        var learned = memories.findByUserIdOrderByUpdatedAtDesc(normalized).stream().filter(m -> m.getSource() != UserMemory.Source.USER_DECLARED).toList();
        memories.deleteAll(learned);
        memories.flush();
        checkIns.findByUserIdOrderByRecordedAtAsc(normalized).forEach(this::learnFromCheckIn);
        for (var recommendation : recommendations.findByUserId(normalized)) {
            feedback.findByRecommendationId(recommendation.getId()).forEach(item -> learnFromRecommendation(recommendation, item.isHelpful()));
        }
        return profile(normalized);
    }

    public void deleteAll(String userId) { memories.deleteByUserId(normalizeUserId(userId)); }

    @Transactional(readOnly = true)
    public ActionAdvice personalizeAction(String userId, CheckIn.Status status, String defaultTitle, String defaultRationale) {
        var learned = memories.findByUserIdAndMemoryKey(normalizeUserId(userId), strategyKey(defaultTitle)).filter(UserMemory::isActive);
        if (learned.isEmpty()) return new ActionAdvice(defaultTitle, defaultRationale, false);
        var memory = learned.get();
        if (memory.getNegativeEvidence() >= memory.getPositiveEvidence()) {
            return switch (status) {
                case TIRED -> new ActionAdvice("물 한 잔을 마시고 5분 쉬어보세요", "이전에 비슷한 추천이 맞지 않았다는 피드백을 반영해 더 부담이 적은 방법으로 바꿨어요.", true);
                case TENSE -> new ActionAdvice("어깨 힘을 빼고 천천히 자세를 바꿔보세요", "이전에 맞지 않았던 방법 대신 몸의 긴장을 가볍게 낮추는 행동을 골랐어요.", true);
                case LOW_FOCUS -> new ActionAdvice("할 일을 하나만 남기고 5분 시작해 보세요", "이전 피드백을 반영해 집중 구간을 더 짧게 조정했어요.", true);
                case OK -> new ActionAdvice("지금의 좋은 흐름을 기록해 두세요", "잘 맞지 않았던 행동을 반복하지 않고 현재 리듬을 관찰하도록 조정했어요.", true);
                case UNCOMFORTABLE -> new ActionAdvice("활동을 멈추고 편한 자세에서 상태를 확인하세요", "이전 피드백과 안전을 우선해 적극적인 활동 대신 관찰을 제안해요.", true);
            };
        }
        var total = memory.getEvidenceCount();
        return new ActionAdvice(defaultTitle, defaultRationale + " 과거 피드백 " + total + "건에서도 이 방법이 잘 맞았어요.", true);
    }

    private void observe(String userId, UserMemory.Type type, String key, String summary, boolean positive, UserMemory.Source source) {
        var normalized = normalizeUserId(userId);
        var memory = memories.findByUserIdAndMemoryKey(normalized, key).orElseGet(() -> new UserMemory(normalized, type, key, summary, source));
        memory.observe(summary, positive, source);
        memories.save(memory);
    }

    private String strategyKey(String title) {
        var normalized = title.toLowerCase(Locale.ROOT).replaceAll("[^가-힣a-z0-9]+", "-");
        return "strategy:" + normalized.substring(0, Math.min(normalized.length(), 150));
    }

    private UserMemory findOwned(UUID id, String userId) {
        var normalized = normalizeUserId(userId);
        return memories.findById(id).filter(m -> m.getUserId().equals(normalized)).orElseThrow(() -> new MemoryNotFoundException(id));
    }

    private String normalizeUserId(String userId) { return userId == null || userId.isBlank() ? "default-user" : userId; }
    private String statusLabel(CheckIn.Status value) { return switch (value) { case OK -> "양호"; case TENSE -> "긴장"; case TIRED -> "피로"; case LOW_FOCUS -> "집중 저하"; case UNCOMFORTABLE -> "불편"; }; }
    private String causeLabel(CheckIn.Cause value) { return switch (value) { case SLEEP -> "수면"; case WORK -> "업무"; case STUDY -> "학업"; case RELATIONSHIP -> "관계"; case PHYSICAL -> "신체"; case UNKNOWN -> "복합 요인"; }; }

    public record Profile(String userId, int activeMemoryCount, int evidenceCount, long helpfulStrategyCount, long avoidStrategyCount, OffsetDateTime lastLearnedAt, boolean personalized) {}
    public record ActionAdvice(String title, String rationale, boolean personalized) {}
    public static class MemoryNotFoundException extends RuntimeException { public MemoryNotFoundException(UUID id) { super("User memory not found: " + id); } }
}
