package app.morrow.api;

import app.morrow.privacy.DataPrivacyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users/me")
public class PrivacyController {
 private final DataPrivacyService service;
 public PrivacyController(DataPrivacyService service){this.service=service;}
 @DeleteMapping("/data") ResponseEntity<Void> deleteAll(@RequestParam(defaultValue="default-user")String userId){service.deleteAllForUser(userId);return ResponseEntity.noContent().build();}
}
