package app.morrow.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.morrow.health.HealthSignalSnapshotRepository;
import app.morrow.checkin.CheckInRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DashboardControllerTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper objectMapper;
 @Autowired HealthSignalSnapshotRepository healthSnapshots;
 @Autowired CheckInRepository checkIns;

 @Test void dashboardHasWellnessDataAndSafetyNotice()throws Exception{
  mvc.perform(get("/api/v1/dashboard"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.wellnessLoad").exists())
   .andExpect(jsonPath("$.score").exists())
   .andExpect(jsonPath("$.metrics").exists())
   .andExpect(jsonPath("$.timeline").isArray())
   .andExpect(jsonPath("$.disclaimer").value("의료 진단이 아닌 일상 웰니스 분석입니다."));
 }

 @Test void nativeHealthSummaryFeedsWebDashboardAndIsIdempotent()throws Exception{
  var userId="native-health-user";
  var payload="""
   {"userId":"native-health-user","clientSnapshotId":"iphone-123","source":"IPHONE","sleepMinutes":392,"heartRate":78,"restingHeartRate":64,"hrv":52,"steps":8123,"activeEnergyKcal":356,"exerciseMinutes":31,"distanceMeters":5400,"flightsClimbed":8,"respiratoryRate":15.2,"oxygenSaturationPercent":98,"recordedAt":"2026-08-09T08:00:00+09:00"}
   """;
  mvc.perform(post("/api/v1/health/snapshots").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
  mvc.perform(post("/api/v1/health/snapshots").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
  mvc.perform(get("/api/v1/dashboard").param("userId",userId))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.metrics.sleepMinutes").value(392))
   .andExpect(jsonPath("$.metrics.steps").value(8123))
   .andExpect(jsonPath("$.metrics.activeEnergyKcal").value(356))
   .andExpect(jsonPath("$.metrics.exerciseMinutes").value(31));
  org.junit.jupiter.api.Assertions.assertEquals(1,healthSnapshots.count());
 }

 @Test void nativeCheckInRetryDoesNotLearnTwice()throws Exception{
  var payload="""
   {"userId":"native-checkin-user","clientEventId":"watch-event-1","status":"TIRED","cause":"SLEEP","note":"","source":"WATCH"}
   """;
  mvc.perform(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
  mvc.perform(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
  org.junit.jupiter.api.Assertions.assertEquals(1,checkIns.findByUserIdOrderByRecordedAtAsc("native-checkin-user").size());
 }

 @Test void checkInCreatesExplainableTimelineAndRecommendation()throws Exception{
  var userId="workflow-user";
  mvc.perform(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON).content("""
   {"userId":"workflow-user","status":"TIRED","cause":"SLEEP","note":"어제 늦게 잠들었음","source":"WEB"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(userId));

  mvc.perform(get("/api/v1/dashboard").param("userId",userId))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.wellnessLoad").value("HIGHER_THAN_USUAL"))
   .andExpect(jsonPath("$.timeline[0].kind").value("CHECKIN"))
   .andExpect(jsonPath("$.timeline[0].userConfirmed").value(true))
   .andExpect(jsonPath("$.recommendation.title").value("7분 동안 가볍게 걸어보세요"));
 }

 @Test void userDataIsIsolatedAndCanBeDeleted()throws Exception{
  var userId="privacy-user";
  mvc.perform(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON).content("""
   {"userId":"privacy-user","status":"OK","cause":"WORK","note":"좋은 흐름","source":"WEB"}
   """)).andExpect(status().isCreated());

  mvc.perform(get("/api/v1/reports/weekly").param("userId",userId))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalCheckIns").value(1));
  mvc.perform(get("/api/v1/reports/weekly").param("userId","unrelated-user"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalCheckIns").value(0));

  mvc.perform(delete("/api/v1/users/me/data").param("userId",userId)).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/reports/weekly").param("userId",userId))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalCheckIns").value(0));
 }

 @Test void crisisLanguageGetsImmediateVerifiedSupportRoute()throws Exception{
  mvc.perform(post("/api/v1/assistant/messages").contentType(MediaType.APPLICATION_JSON).content("""
   {"userId":"safety-user","content":"죽고 싶어"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.safetyChecked").value(true))
   .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("109")))
   .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("119")));
 }

 @Test void feedbackBecomesLongTermMemoryAndChangesTheNextAction()throws Exception{
  var userId="learning-user";
  mvc.perform(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON).content("""
   {"userId":"learning-user","status":"TIRED","cause":"SLEEP","note":"잠을 설침","source":"WEB"}
   """)).andExpect(status().isCreated());

  var dashboard=mvc.perform(get("/api/v1/dashboard").param("userId",userId)).andExpect(status().isOk()).andReturn();
  var recommendationId=objectMapper.readTree(dashboard.getResponse().getContentAsString()).path("recommendation").path("id").asText();
  mvc.perform(post("/api/v1/recommendations/{id}/feedback",recommendationId).contentType(MediaType.APPLICATION_JSON).content("""
   {"completed":true,"helpful":false,"note":"걷기는 지금 부담스러웠음"}
   """)).andExpect(status().isCreated());

  mvc.perform(get("/api/v1/personalization/profile").param("userId",userId))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.personalized").value(true))
   .andExpect(jsonPath("$.evidenceCount").value(2))
   .andExpect(jsonPath("$.avoidStrategyCount").value(1));

  mvc.perform(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON).content("""
   {"userId":"learning-user","status":"TIRED","cause":"SLEEP","note":"오늘도 피곤함","source":"WEB"}
   """)).andExpect(status().isCreated());
  mvc.perform(get("/api/v1/dashboard").param("userId",userId))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.recommendation.title").value("물 한 잔을 마시고 5분 쉬어보세요"))
   .andExpect(jsonPath("$.recommendation.rationale").value(org.hamcrest.Matchers.containsString("피드백")));

  mvc.perform(post("/api/v1/assistant/messages").contentType(MediaType.APPLICATION_JSON).content("""
   {"userId":"learning-user","content":"지금 뭘 하면 좋을까?"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.aiMode").value("FALLBACK"))
   .andExpect(jsonPath("$.personalized").value(true))
   .andExpect(jsonPath("$.personalizationEvidenceCount").value(3));
 }

 @Test void declaredMemoryIsUserControlledAndDeletedWithAllWellnessData()throws Exception{
  var userId="memory-owner";
  mvc.perform(post("/api/v1/personalization/memories").contentType(MediaType.APPLICATION_JSON).content("""
   {"userId":"memory-owner","type":"PREFERENCE","summary":"강한 운동보다 짧은 산책을 선호함"}
   """)).andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("USER_DECLARED"));
  mvc.perform(get("/api/v1/personalization/profile").param("userId",userId))
   .andExpect(status().isOk()).andExpect(jsonPath("$.activeMemoryCount").value(1));
  mvc.perform(delete("/api/v1/users/me/data").param("userId",userId)).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/personalization/profile").param("userId",userId))
   .andExpect(status().isOk()).andExpect(jsonPath("$.activeMemoryCount").value(0));
 }
}
