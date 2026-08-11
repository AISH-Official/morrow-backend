package app.morrow.api;

import app.morrow.privacy.DataPrivacyService;
import app.morrow.auth.RequestUserResolver;
import app.morrow.auth.AccountAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users/me")
public class PrivacyController {
 private final DataPrivacyService service; private final RequestUserResolver users; private final AccountAuthService accounts;
 public PrivacyController(DataPrivacyService service,RequestUserResolver users,AccountAuthService accounts){this.service=service;this.users=users;this.accounts=accounts;}
 @DeleteMapping("/data") ResponseEntity<Void> deleteAll(@RequestParam(defaultValue="default-user")String userId){service.deleteAllForUser(users.resolve(userId));return ResponseEntity.noContent().build();}
 @DeleteMapping("/account") ResponseEntity<Void> deleteAccount(@RequestParam(defaultValue="default-user")String userId){var resolved=users.resolve(userId);service.deleteAllForUser(resolved);accounts.deleteAccount(resolved);return ResponseEntity.noContent().build();}
}
