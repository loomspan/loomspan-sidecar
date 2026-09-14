package ai.loomspan.sidecar.api;

import java.util.List;

import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillDescriptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/skills")
final class SkillCatalogController {
    private final SkillCatalog catalog;

    SkillCatalogController(SkillCatalog catalog) { this.catalog = catalog; }

    @GetMapping
    List<SkillDescriptor> skills() { return catalog.skills(); }

    @GetMapping("/{name}")
    SkillDescriptor skill(@PathVariable("name") String name) {
        return catalog.skill(name).orElseThrow(() -> new ResourceNotFoundException("Unknown skill '" + name + "'"));
    }
}
