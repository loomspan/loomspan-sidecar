package ai.loomspan.sidecar.management;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/personal-tokens")
public final class ManagementPersonalTokenController {
    public record Create(String preset, Instant expiresAt) {}
    private final ManagementPersonalTokenService tokens;
    public ManagementPersonalTokenController(ManagementPersonalTokenService tokens) { this.tokens = tokens; }
    @GetMapping public ResponseEntity<List<ManagementPersonalTokenService.Metadata>> list(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.list(ManagementController.principal(auth).id()));
    }
    @PostMapping public ResponseEntity<ManagementPersonalTokenService.Issued> issue(@RequestBody Create body, Authentication auth) {
        var user = ManagementController.principal(auth);
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(tokens.issue(user.id(), user.version(), body.preset(), body.expiresAt()));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> revoke(@PathVariable("id") String id, Authentication auth) {
        tokens.revoke(ManagementController.principal(auth).id(), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @ExceptionHandler({ManagementPersonalTokenService.Rejected.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> rejected() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("error", "Personal token request could not be completed"));
    }
}
