package app.morrow.api;

import app.morrow.auth.RequestUserResolver;
import app.morrow.demo.DemoScenarioService;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/demo/scenarios")
public class DemoScenarioController {
    private final DemoScenarioService scenarios;
    private final RequestUserResolver users;

    public DemoScenarioController(DemoScenarioService scenarios, RequestUserResolver users) {
        this.scenarios = scenarios;
        this.users = users;
    }

    @PostMapping("/{scenario}")
    DemoScenarioResponse apply(@PathVariable DemoScenarioService.Scenario scenario) {
        return DemoScenarioResponse.from(scenarios.apply(users.resolve("default-user"), scenario));
    }

    record DemoScenarioResponse(String scenario, String title, String summary, OffsetDateTime generatedAt) {
        static DemoScenarioResponse from(DemoScenarioService.DemoResult value) {
            return new DemoScenarioResponse(value.scenario(), value.title(), value.summary(), value.generatedAt());
        }
    }
}
