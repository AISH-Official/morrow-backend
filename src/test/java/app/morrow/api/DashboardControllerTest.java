package app.morrow.api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc class DashboardControllerTest {
 @Autowired MockMvc mvc;
 @Test void dashboardHasWellnessDataAndSafetyNotice()throws Exception{mvc.perform(get("/api/v1/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.wellnessLoad").exists()).andExpect(jsonPath("$.score").exists()).andExpect(jsonPath("$.metrics").exists()).andExpect(jsonPath("$.timeline").exists()).andExpect(jsonPath("$.disclaimer").value("의료 진단이 아닌 일상 웰니스 분석입니다."));}
}
