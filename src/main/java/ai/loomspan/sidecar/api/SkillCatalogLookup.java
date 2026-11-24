package ai.loomspan.sidecar.api;

import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillDescriptor;
import ai.loomspan.api.SkillKind;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.ValidatedSkill;
import org.springframework.stereotype.Component;

@Component
final class SkillCatalogLookup {
    private final SkillReloader reloader;

    SkillCatalogLookup(SkillReloader reloader) {
        this.reloader = reloader;
    }

    SkillCatalog snapshot(String namespace) {
        try {
            return reloader.snapshot(namespace);
        } catch (IllegalArgumentException failure) {
            throw new ApiInputException(failure.getMessage());
        } catch (SkillReloadException failure) {
            throw new ResourceNotFoundException(failure.getMessage());
        }
    }

    SkillDescriptor skill(String namespace, String localName) {
        try {
            // Check the complete pair before attempting to find the namespace.
            new ValidatedSkill(namespace, localName, SkillKind.YAML);
        } catch (IllegalArgumentException failure) {
            throw new ApiInputException(failure.getMessage());
        }
        return snapshot(namespace).skill(localName)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown skill '" + namespace + ":" + localName + "'"));
    }
}
