package app.morrow.api;

import app.morrow.privacy.DataPrivacyService;
import app.morrow.auth.RequestUserResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users/me")
public class PrivacyController {
 private final DataPrivacyService service; private final RequestUserResolver users;
 public PrivacyController(DataPrivacyService service,RequestUserResolver users){this.service=service;this.users=users;}
 @DeleteMapping("/data") ResponseEntity<Void> deleteAll(@RequestParam(defaultValue="default-user")String userId){service.deleteAllForUser(users.resolve(userId));return ResponseEntity.noContent().build();}
}
