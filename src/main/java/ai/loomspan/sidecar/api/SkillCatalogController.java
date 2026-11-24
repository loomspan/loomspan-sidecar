package ai.loomspan.sidecar.api;

import java.util.List;

import ai.loomspan.api.SkillDescriptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/namespaces/{namespace}/skills")
final class SkillCatalogController {
    private final SkillCatalogLookup catalog;

    SkillCatalogController(SkillCatalogLookup catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<SkillDescriptor> skills(@PathVariable("namespace") String namespace) {
        return catalog.snapshot(namespace).skills();
    }

    @GetMapping("/{localName}")
    SkillDescriptor skill(@PathVariable("namespace") String namespace, @PathVariable("localName") String localName) {
        return catalog.skill(namespace, localName);
    }
}
