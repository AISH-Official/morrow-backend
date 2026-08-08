package app.morrow.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DashboardControllerTest {
 @Autowired MockMvc mvc;

 @Test void dashboardHasWellnessDataAndSafetyNotice()throws Exception{
  mvc.perform(get("/api/v1/dashboard"))
   .andExpect(status().isOk())
   .andExpect(jsonPath("$.wellnessLoad").exists())
   .andExpect(jsonPath("$.score").exists())
   .andExpect(jsonPath("$.metrics").exists())
   .andExpect(jsonPath("$.timeline").isArray())
   .andExpect(jsonPath("$.disclaimer").value("의료 진단이 아닌 일상 웰니스 분석입니다."));
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
}
