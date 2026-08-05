package app.morrow.api;
import app.morrow.checkin.CheckInService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/users/me")
public class PrivacyController {
 private final CheckInService service; public PrivacyController(CheckInService service){this.service=service;}
 @DeleteMapping("/data") ResponseEntity<Void> deleteAll(){service.deleteAll();return ResponseEntity.noContent().build();}
}
