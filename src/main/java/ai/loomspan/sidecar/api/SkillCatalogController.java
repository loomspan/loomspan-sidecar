package ai.loomspan.sidecar.api;

import java.util.List;

import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillDescriptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/skills")
final class SkillCatalogController {
    private final SkillReloader reloader;

    SkillCatalogController(SkillReloader reloader) { this.reloader = reloader; }

    @GetMapping
    List<SkillDescriptor> skills() { return reloader.snapshot("default").skills(); }

    @GetMapping("/{name}")
    SkillDescriptor skill(@PathVariable("name") String name) {
        return reloader.snapshot("default").skill(name.contains("-") ? name : "default-" + name).orElseThrow(() -> new ResourceNotFoundException("Unknown skill '" + name + "'"));
    }
}
