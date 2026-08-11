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
import app.morrow.auth.AccountLinkRepository;
import app.morrow.assistant.UserContextCollector;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
 "morrow.demo-login.enabled=true",
 "morrow.demo-login.username=사용자",
 "morrow.demo-login.password=morrow1234",
 "morrow.demo-login.user-id=hackathon-demo",
 "morrow.assistant.include-health-data=true"
}) @AutoConfigureMockMvc
class DashboardControllerTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper objectMapper;
 @Autowired HealthSignalSnapshotRepository healthSnapshots;
 @Autowired CheckInRepository checkIns;
 @Autowired AccountLinkRepository accountLinks;
 @Autowired UserContextCollector userContextCollector;

 @Test void githubPagesOriginCanCallApi()throws Exception{
  mvc.perform(options("/api/v1/dashboard")
    .header("Origin","https://aish-official.github.io")
    .header("Access-Control-Request-Method","GET"))
   .andExpect(status().isOk())
   .andExpect(header().string("Access-Control-Allow-Origin","https://aish-official.github.io"));
 }

 @Test void dashboardHasWellnessDataAndSafetyNotice()throws Exception{
  mvc.perform(get("/api/v1/dashboard"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.wellnessLoad").exists())
   .andExpect(jsonPath("$.score").exists())
   .andExpect(jsonPath("$.metrics").exists())
   .andExpect(jsonPath("$.timeline").isArray())
   .andExpect(jsonPath("$.disclaimer").value("의료 진단이 아닌 일상 웰니스 분석입니다."));
 }

 @Test void hackathonDemoAccountCanLoginAndWrongPasswordIsRejected()throws Exception{
  mvc.perform(post("/api/v1/auth/device").contentType(MediaType.APPLICATION_JSON).content("""
   {"deviceId":"hackathon-login-test","deviceName":"Judge iPhone","platform":"IOS"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.startsWith("user-")));

  mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
   {"username":"사용자","password":"morrow1234","deviceId":"hackathon-login-test","deviceName":"Judge iPhone","platform":"IOS"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value("hackathon-demo"))
   .andExpect(jsonPath("$.accessToken").isNotEmpty());

  mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
   {"username":"사용자","password":"wrong","deviceId":"hackathon-login-fail","deviceName":"Judge iPhone","platform":"IOS"}
   """))
   .andExpect(status().isUnauthorized());
 }

 @Test void publicDeviceRegistrationCannotClaimAUserOrRotateAnExistingDevice()throws Exception{
  var anonymousLogin=mvc.perform(post("/api/v1/auth/device").contentType(MediaType.APPLICATION_JSON).content("""
   {"deviceId":"security-device","deviceName":"Untrusted Device","platform":"WEB","userId":"hackathon-demo"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.startsWith("user-")))
   .andReturn();
  var anonymousToken=objectMapper.readTree(anonymousLogin.getResponse().getContentAsString()).path("accessToken").asText();

  mvc.perform(patch("/api/v1/privacy/ai-health-consent")
    .header("Authorization","Bearer "+anonymousToken)
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"consent\":true}"))
   .andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.message").value("연결된 계정을 찾을 수 없습니다."));

  mvc.perform(post("/api/v1/auth/device").contentType(MediaType.APPLICATION_JSON).content("""
   {"deviceId":"security-device","deviceName":"Takeover Attempt","platform":"WEB","userId":"hackathon-demo"}
   """))
   .andExpect(status().isConflict());
 }

 @Test void demoConsentRepairsMissingAccountLinkAndIsSharedWithWatchAi()throws Exception{
  var phoneLogin=mvc.perform(post("/api/v1/auth/account-login").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"사용자","password":"morrow1234","deviceId":"consent-iphone","deviceName":"Demo iPhone","platform":"IOS"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value("hackathon-demo"))
   .andReturn();
  var phoneToken=objectMapper.readTree(phoneLogin.getResponse().getContentAsString()).path("accessToken").asText();

  accountLinks.findByUserId("hackathon-demo").ifPresent(accountLinks::delete);
  mvc.perform(patch("/api/v1/privacy/ai-health-consent")
    .header("Authorization","Bearer "+phoneToken)
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"consent\":true}"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.consent").value(true));

  var watchLogin=mvc.perform(post("/api/v1/auth/account-login").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"사용자","password":"morrow1234","deviceId":"consent-watch","deviceName":"Demo Watch","platform":"WATCHOS"}
   """))
   .andExpect(status().isCreated()).andReturn();
  var watchToken=objectMapper.readTree(watchLogin.getResponse().getContentAsString()).path("accessToken").asText();
  mvc.perform(get("/api/v1/privacy/ai-health-consent").header("Authorization","Bearer "+watchToken))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.consent").value(true));

  mvc.perform(post("/api/v1/health/snapshots")
    .header("Authorization","Bearer "+watchToken)
    .contentType(MediaType.APPLICATION_JSON)
    .content("""
     {"userId":"hackathon-demo","clientSnapshotId":"consent-watch-health","source":"WATCH","heartRate":72,"hrv":48,"steps":4200}
     """))
   .andExpect(status().isCreated());
  org.junit.jupiter.api.Assertions.assertFalse(userContextCollector.collectContext("hackathon-demo").recentHealthSnapshots().isEmpty());

  mvc.perform(patch("/api/v1/privacy/ai-health-consent")
    .header("Authorization","Bearer "+phoneToken)
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"consent\":false}"))
   .andExpect(status().isOk());
  org.junit.jupiter.api.Assertions.assertTrue(userContextCollector.collectContext("hackathon-demo").recentHealthSnapshots().isEmpty());
 }

 @Test void signupCreatesIsolatedAccountAndPasswordLoginKeepsItsUser()throws Exception{
  var signup=mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"new-member","password":"healthy1234","deviceId":"signup-web","deviceName":"Signup Browser","platform":"WEB"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.startsWith("account-")))
   .andReturn();
  var userId=objectMapper.readTree(signup.getResponse().getContentAsString()).path("userId").asText();

  mvc.perform(post("/api/v1/auth/account-login").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"new-member","password":"healthy1234","deviceId":"login-web","deviceName":"Returning Browser","platform":"WEB"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(userId));

  mvc.perform(post("/api/v1/auth/account").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"new-member","deviceId":"legacy-bypass","deviceName":"Old Client","platform":"WEB"}
   """))
   .andExpect(status().isUnauthorized());

  mvc.perform(post("/api/v1/auth/account-login").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"new-member","password":"wrong-password","deviceId":"wrong-web","deviceName":"Wrong Browser","platform":"WEB"}
   """))
   .andExpect(status().isUnauthorized());

  mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"new-member","password":"another1234","deviceId":"duplicate-web","deviceName":"Duplicate Browser","platform":"WEB"}
   """))
   .andExpect(status().isConflict());

  mvc.perform(post("/api/v1/auth/account-login").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"사용자","password":"morrow1234","deviceId":"demo-new-login","deviceName":"Demo Browser","platform":"WEB"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value("hackathon-demo"));
 }

 @Test void existingPasswordlessAccountCanBecomeARegisteredAccountWithoutLosingItsUser()throws Exception{
  var legacy=mvc.perform(post("/api/v1/auth/account").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"legacy-member","deviceId":"legacy-web","deviceName":"Legacy Browser","platform":"WEB"}
   """))
   .andExpect(status().isCreated()).andReturn();
  var userId=objectMapper.readTree(legacy.getResponse().getContentAsString()).path("userId").asText();

  mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"legacy-member","password":"converted1234","deviceId":"converted-web","deviceName":"Converted Browser","platform":"WEB"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(userId));

  mvc.perform(post("/api/v1/auth/account").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"legacy-member","deviceId":"legacy-after-conversion","deviceName":"Old Client","platform":"WEB"}
   """))
   .andExpect(status().isUnauthorized());
 }

 @Test void demoScenarioBuildsTheClosedRecoveryLoopForJudging()throws Exception{
  var login=mvc.perform(post("/api/v1/auth/account-login").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"사용자","password":"morrow1234","deviceId":"aac-demo-browser","deviceName":"AAC Judge","platform":"WEB"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value("hackathon-demo"))
   .andReturn();
  var token=objectMapper.readTree(login.getResponse().getContentAsString()).path("accessToken").asText();

  mvc.perform(post("/api/v1/demo/scenarios/TENSION").header("Authorization","Bearer "+token))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.scenario").value("TENSION"))
   .andExpect(jsonPath("$.title").value("발표 전 긴장 상승"));
  mvc.perform(post("/api/v1/demo/scenarios/TENSION").header("Authorization","Bearer "+token))
   .andExpect(status().isOk());

  mvc.perform(get("/api/v1/dashboard").header("Authorization","Bearer "+token))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.metrics.restingHeartRate").value(82))
   .andExpect(jsonPath("$.scoreReasons[0]").value(org.hamcrest.Matchers.containsString("안정 심박")))
   .andExpect(jsonPath("$.timeline.length()").value(1))
   .andExpect(jsonPath("$.recommendation").exists());
  mvc.perform(get("/api/v1/reports/weekly").header("Authorization","Bearer "+token))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.suggestedRecoveryCount").value(5))
   .andExpect(jsonPath("$.completedRecoveryCount").value(4))
   .andExpect(jsonPath("$.recoveryHelpfulRate").value(75.0))
   .andExpect(jsonPath("$.topHelpfulAction").value("1분 호흡"));
 }

 @Test void accountIdLoginKeepsThePhoneLinkAfterLoggingInAgain()throws Exception{
  var firstLogin=mvc.perform(post("/api/v1/auth/account").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"subin","deviceId":"account-web-first","deviceName":"First Browser","platform":"WEB"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.accessToken").isNotEmpty())
   .andReturn();
  var firstToken=objectMapper.readTree(firstLogin.getResponse().getContentAsString()).path("accessToken").asText();

  var phone=mvc.perform(post("/api/v1/auth/device").contentType(MediaType.APPLICATION_JSON).content("""
   {"deviceId":"account-phone","deviceName":"Linked iPhone","platform":"IOS"}
   """))
   .andExpect(status().isCreated()).andReturn();
  var phoneCredentials=objectMapper.readTree(phone.getResponse().getContentAsString());
  var phoneUserId=phoneCredentials.path("userId").asText();

  mvc.perform(post("/api/v1/auth/pair")
    .contentType(MediaType.APPLICATION_JSON)
    .content(objectMapper.writeValueAsString(java.util.Map.of(
     "pairingCode",phoneCredentials.path("pairingCode").asText(),"deviceId","code-only-login","deviceName","Code only","platform","WEB"
    ))))
   .andExpect(status().isUnauthorized());

  var paired=mvc.perform(post("/api/v1/auth/pair")
    .header("Authorization","Bearer "+firstToken)
    .contentType(MediaType.APPLICATION_JSON)
    .content(objectMapper.writeValueAsString(java.util.Map.of(
     "pairingCode",phoneCredentials.path("pairingCode").asText(),"deviceId","account-web-first","deviceName","First Browser","platform","WEB"
    ))))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(phoneUserId))
   .andReturn();
  var pairedToken=objectMapper.readTree(paired.getResponse().getContentAsString()).path("accessToken").asText();
  mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+pairedToken)).andExpect(status().isNoContent());

  mvc.perform(post("/api/v1/auth/account").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"subin","deviceId":"account-web-return","deviceName":"Returning Browser","platform":"WEB"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(phoneUserId));
 }

 @Test void nativeHealthSummaryFeedsWebDashboardAndIsIdempotent()throws Exception{
  var userId="native-health-user";
  var payload="""
   {"userId":"native-health-user","clientSnapshotId":"iphone-123","source":"IPHONE","sleepMinutes":392,"heartRate":78,"restingHeartRate":64,"hrv":52,"steps":8123,"activeEnergyKcal":356,"exerciseMinutes":31,"distanceMeters":5400,"flightsClimbed":8,"respiratoryRate":15.2,"oxygenSaturationPercent":98,"recordedAt":"2026-08-09T08:00:00+09:00"}
   """;
  mvc.perform(post("/api/v1/health/snapshots").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
  mvc.perform(post("/api/v1/health/snapshots").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
  var refreshedPayload=payload.replace("\"steps\":8123","\"steps\":9345").replace("\"activeEnergyKcal\":356","\"activeEnergyKcal\":402");
  mvc.perform(post("/api/v1/health/snapshots").contentType(MediaType.APPLICATION_JSON).content(refreshedPayload)).andExpect(status().isCreated());
  mvc.perform(get("/api/v1/dashboard").param("userId",userId))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.score").value(80))
   .andExpect(jsonPath("$.wellnessLoad").value("NORMAL"))
   .andExpect(jsonPath("$.metrics.sleepMinutes").value(392))
   .andExpect(jsonPath("$.metrics.steps").value(9345))
   .andExpect(jsonPath("$.metrics.activeEnergyKcal").value(402))
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

 @Test void utcCheckInIsDisplayedInKoreanTime()throws Exception{
  var koreanTime=java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0);
  var utcTime=koreanTime.withOffsetSameInstant(java.time.ZoneOffset.UTC);
  mvc.perform(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of(
   "userId","kst-user","clientEventId","kst-event","status","OK","cause","WORK","note","시간 확인","source","WEB","recordedAt",utcTime.toString()
  )))).andExpect(status().isCreated());

  mvc.perform(get("/api/v1/dashboard").param("userId","kst-user"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.timeline[0].time").value(koreanTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))));
 }

 @Test void timelineIsSortedByDisplayedKoreanTimeEvenWhenSyncedOutOfOrder()throws Exception{
  var date=java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Seoul")).toLocalDate();
  var late=date.atTime(18,30).atZone(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime();
  var early=date.atTime(8,15).atZone(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime();
  for(var value:java.util.List.of(java.util.Map.of("id","late","time",late),java.util.Map.of("id","early","time",early))){
   mvc.perform(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of(
    "userId","timeline-order-user","clientEventId",value.get("id"),"status","OK","cause","WORK","note","정렬 확인","source","WEB","recordedAt",value.get("time").toString()
   )))).andExpect(status().isCreated());
  }

  mvc.perform(get("/api/v1/dashboard").param("userId","timeline-order-user"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.timeline[0].time").value("08:15"))
   .andExpect(jsonPath("$.timeline[1].time").value("18:30"));
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
   .andExpect(jsonPath("$.wellnessLoad").value("MODERATE"))
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

 @Test void proactiveInsightSkipsWhenNoRecentSignalsExist()throws Exception{
  mvc.perform(post("/api/v1/assistant/proactive-insight").contentType(MediaType.APPLICATION_JSON).content("""
   {"userId":"quiet-user"}
   """))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.shouldNotify").value(false))
   .andExpect(jsonPath("$.reason").value("NO_RECENT_SIGNALS"));
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

 @Test void devicePairingSharesOneUserAndBearerPreventsCrossUserAccess()throws Exception{
  var account=mvc.perform(post("/api/v1/auth/account").contentType(MediaType.APPLICATION_JSON).content("""
   {"accountId":"pair-test","deviceId":"web-test-device","deviceName":"Test Browser","platform":"WEB"}
   """))
   .andExpect(status().isCreated()).andReturn();
  var accountToken=objectMapper.readTree(account.getResponse().getContentAsString()).path("accessToken").asText();
  var phone=mvc.perform(post("/api/v1/auth/device").contentType(MediaType.APPLICATION_JSON).content("""
   {"deviceId":"ios-test-device","deviceName":"Test iPhone","platform":"IOS"}
   """))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.startsWith("user-")))
   .andReturn();
  var phoneCredentials=objectMapper.readTree(phone.getResponse().getContentAsString());
  var userId=phoneCredentials.path("userId").asText();
  var pairingCode=phoneCredentials.path("pairingCode").asText();

  var web=mvc.perform(post("/api/v1/auth/pair").header("Authorization","Bearer "+accountToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of(
   "pairingCode",pairingCode,"deviceId","web-test-device","deviceName","Test Browser","platform","WEB"
  ))))
   .andExpect(status().isCreated())
   .andExpect(jsonPath("$.userId").value(userId))
   .andReturn();
  var webToken=objectMapper.readTree(web.getResponse().getContentAsString()).path("accessToken").asText();

  mvc.perform(get("/api/v1/dashboard").param("userId",userId).header("Authorization","Bearer "+webToken))
   .andExpect(status().isOk());
  mvc.perform(get("/api/v1/dashboard").param("userId","somebody-else").header("Authorization","Bearer "+webToken))
   .andExpect(status().isForbidden());
 }

 @Test void logoutRevokesTheCurrentDeviceToken()throws Exception{
  var device=mvc.perform(post("/api/v1/auth/device").contentType(MediaType.APPLICATION_JSON).content("""
    {"deviceId":"logout-device","deviceName":"Logout test","platform":"WEB"}
    """))
   .andExpect(status().isCreated()).andReturn();
  var credentials=objectMapper.readTree(device.getResponse().getContentAsString());
  var userId=credentials.path("userId").asText();
  var authorization="Bearer "+credentials.path("accessToken").asText();

  mvc.perform(get("/api/v1/dashboard").param("userId",userId).header("Authorization",authorization))
   .andExpect(status().isOk());
  mvc.perform(post("/api/v1/auth/logout").header("Authorization",authorization))
   .andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/dashboard").param("userId",userId).header("Authorization",authorization))
   .andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/auth/logout").header("Authorization",authorization))
   .andExpect(status().isNoContent());
 }

 @Test void authenticatedDeviceCanRegisterIosAndWatchPushTokens()throws Exception{
  var device=mvc.perform(post("/api/v1/auth/device").contentType(MediaType.APPLICATION_JSON).content("""
   {"deviceId":"push-owner-device","deviceName":"Push Owner","platform":"IOS"}
   """))
   .andExpect(status().isCreated()).andReturn();
  var credentials=objectMapper.readTree(device.getResponse().getContentAsString());
  var userId=credentials.path("userId").asText();
  var token=credentials.path("accessToken").asText();
  var authorization="Bearer "+token;

  mvc.perform(post("/api/v1/notifications/devices").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of(
   "userId",userId,"deviceToken","a".repeat(64),"platform","IOS","environment","SANDBOX"
  )))).andExpect(status().isCreated()).andExpect(jsonPath("$.platform").value("IOS"));
  mvc.perform(post("/api/v1/notifications/devices").header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of(
   "userId",userId,"deviceToken","b".repeat(64),"platform","WATCHOS","environment","SANDBOX"
  )))).andExpect(status().isCreated()).andExpect(jsonPath("$.platform").value("WATCHOS"));
  mvc.perform(post("/api/v1/notifications/test").header("Authorization",authorization).param("userId",userId))
   .andExpect(status().isOk()).andExpect(jsonPath("$.attempted").value(2));
 }
}
