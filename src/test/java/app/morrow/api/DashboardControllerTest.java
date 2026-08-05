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
 @Test void dashboardHasRecommendationAndSafetyNotice()throws Exception{mvc.perform(get("/api/v1/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.recommendation.id").value("walk-7")).andExpect(jsonPath("$.disclaimer").exists());}
}
