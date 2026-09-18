package ai.loomspan.sidecar.rest;

import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillKind;
import ai.loomspan.api.ValidatedSkill;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public final class RestRouteCatalogValidator {
    public void validate(RestRouteConfiguration configuration, SkillCatalog catalog) {
        validateKinds(configuration, catalog.skills().stream().collect(java.util.stream.Collectors.toMap(
                descriptor -> descriptor.name(), descriptor -> descriptor.kind())));
    }

    public void validateCandidate(RestRouteConfiguration configuration, java.util.List<ValidatedSkill> skills) {
        validateKinds(configuration, skills.stream().collect(java.util.stream.Collectors.toMap(
                ValidatedSkill::name, ValidatedSkill::kind)));
    }

    private void validateKinds(RestRouteConfiguration configuration, java.util.Map<String, SkillKind> kinds) {
        Set<String> restSkills = new LinkedHashSet<>();
        kinds.forEach((name, kind) -> {
            if (kind == SkillKind.REST) restSkills.add(name);
        });
        for (String skill : restSkills) {
            if (!configuration.routes().containsKey(skill))
                fail(configuration, "routes." + skill, "REST skill '" + skill + "' has no route");
        }
        configuration.routes().forEach((name, route) -> {
            var kind = kinds.get(name);
            if (kind == null) fail(configuration, "routes." + route.sourceName(),
                    "route '" + route.sourceName() + "' names an unknown skill");
            if (kind != SkillKind.REST)
                fail(configuration, "routes." + route.sourceName(),
                        "route '" + route.sourceName() + "' names a non-REST skill");
            if (!configuration.targets().containsKey(route.target()))
                fail(configuration, "routes." + route.sourceName() + ".target",
                        "route '" + route.sourceName() + "' names an unknown target");
        });
    }

    private void fail(RestRouteConfiguration configuration, String location, String reason) {
        throw failure(configuration, location, reason);
    }
    private IllegalStateException failure(RestRouteConfiguration configuration, String location, String reason) {
        return new IllegalStateException("Invalid REST routes at " + configuration.location() + " [" + location + "]: " + reason);
    }
}
