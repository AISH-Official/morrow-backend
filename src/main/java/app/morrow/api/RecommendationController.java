package app.morrow.api;
import app.morrow.recommendation.RecommendationFeedback;
import app.morrow.recommendation.RecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/recommendations")
public class RecommendationController {
 private final RecommendationService service; public RecommendationController(RecommendationService service){this.service=service;}
 @PostMapping("/{id}/feedback") ResponseEntity<FeedbackResponse> createFeedback(@PathVariable UUID id,@Valid @RequestBody FeedbackRequest request){var feedback=service.createFeedback(id,new RecommendationService.CreateFeedback(request.completed(),request.helpful(),request.note()));return ResponseEntity.created(URI.create("/api/v1/recommendations/"+id+"/feedback/"+feedback.getId())).body(FeedbackResponse.from(feedback));}
 @ExceptionHandler(RecommendationService.RecommendationNotFoundException.class) ResponseEntity<ErrorResponse> handleNotFound(RecommendationService.RecommendationNotFoundException ex){return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));}
 record FeedbackRequest(@NotNull Boolean completed,@NotNull Boolean helpful,@Size(max=500)String note){}
 record FeedbackResponse(UUID id,UUID recommendationId,boolean completed,boolean helpful,String note,OffsetDateTime createdAt){static FeedbackResponse from(RecommendationFeedback f){return new FeedbackResponse(f.getId(),f.getRecommendationId(),f.isCompleted(),f.isHelpful(),f.getNote(),f.getCreatedAt());}}
 record ErrorResponse(String message){}
}
