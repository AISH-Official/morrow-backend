package app.morrow.api;

import app.morrow.auth.AccountAuthService;
import app.morrow.auth.RequestUserResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/privacy")
public class ConsentController {
    private final AccountAuthService accounts;
    private final RequestUserResolver users;

    public ConsentController(AccountAuthService accounts, RequestUserResolver users) { this.accounts = accounts; this.users = users; }

    @GetMapping("/ai-health-consent") ConsentResponse get() {
        var userId = users.resolve("default-user");
        return new ConsentResponse(accounts.aiHealthConsent(userId));
    }

    @PatchMapping("/ai-health-consent") ConsentResponse update(@Valid @RequestBody ConsentRequest request) {
        return new ConsentResponse(accounts.updateAiHealthConsent(users.resolve("default-user"), request.consent()));
    }

    record ConsentRequest(@NotNull Boolean consent) {}
    record ConsentResponse(boolean consent) {}
}
