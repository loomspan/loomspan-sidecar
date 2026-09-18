package ai.loomspan.sidecar.api;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.sidecar.execution.ExecutionCoordinator;
import ai.loomspan.sidecar.execution.ExecutionFailure;
import ai.loomspan.sidecar.execution.ExecutionSnapshot;
import ai.loomspan.sidecar.security.ExecutionOwner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/v1")
final class ExecutionController {
    private final SkillReloader reloader;
    private final SkillTemplate template;
    private final LimitedJsonObjectReader reader;
    private final ExecutionCoordinator coordinator;

    ExecutionController(SkillReloader reloader, SkillTemplate template, LimitedJsonObjectReader reader,
            ExecutionCoordinator coordinator) {
        this.reloader = reloader;
        this.template = template;
        this.reader = reader;
        this.coordinator = coordinator;
    }

    @PostMapping("/skills/{name}/executions")
    ResponseEntity<Map<String, UUID>> execute(@PathVariable("name") String name, HttpServletRequest request,
            JwtAuthenticationToken authentication) throws IOException {
        if (reloader.snapshot().skill(name).isEmpty()) throw new ResourceNotFoundException("Unknown skill '" + name + "'");
        Map<String, Object> input = reader.read(request);
        template.validate(name, input);
        UUID id = coordinator.admit(name, input, reader.measure(input),
                ExecutionOwner.from(authentication), authentication);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/v1/executions/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.accepted().location(location).body(Map.of("id", id));
    }

    @GetMapping("/executions/{id}")
    Map<String, Object> execution(@PathVariable("id") UUID id, JwtAuthenticationToken authentication) {
        ExecutionSnapshot snapshot = coordinator.find(id, ExecutionOwner.from(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", snapshot.id());
        response.put("skillName", snapshot.skillName());
        response.put("status", snapshot.status());
        response.put("createdAt", snapshot.createdAt());
        response.put("completedAt", snapshot.completedAt());
        response.put("result", snapshot.result());
        response.put("failure", failure(snapshot.failure()));
        response.put("configurationSnapshotId", snapshot.configurationSnapshotId());
        if (snapshot.events() != null) response.put("events", snapshot.events());
        return response;
    }

    private Map<String, Object> failure(ExecutionFailure failure) {
        if (failure == null) return null;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("kind", failure.kind());
        response.put("message", failure.message());
        if (!failure.issues().isEmpty()) response.put("issues", failure.issues());
        return response;
    }
}
