package ai.loomspan.sidecar.rest;

import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillKind;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public final class RestRouteCatalogValidator {
    public void validate(RestRouteConfiguration configuration, SkillCatalog catalog) {
        Set<String> restSkills = new LinkedHashSet<>();
        catalog.skills().forEach(descriptor -> {
            if (descriptor.kind() == SkillKind.REST) restSkills.add(descriptor.name());
        });
        for (String skill : restSkills) {
            if (!configuration.routes().containsKey(skill))
                fail(configuration, "routes." + skill, "REST skill '" + skill + "' has no route");
        }
        configuration.routes().forEach((name, route) -> {
            var descriptor = catalog.skill(name).orElseThrow(() ->
                    failure(configuration, "routes." + name, "route '" + name + "' names an unknown skill"));
            if (descriptor.kind() != SkillKind.REST)
                fail(configuration, "routes." + name, "route '" + name + "' names a non-REST skill");
            if (!configuration.targets().containsKey(route.target()))
                fail(configuration, "routes." + name + ".target", "route '" + name + "' names an unknown target");
        });
    }

    private void fail(RestRouteConfiguration configuration, String location, String reason) {
        throw failure(configuration, location, reason);
    }
    private IllegalStateException failure(RestRouteConfiguration configuration, String location, String reason) {
        return new IllegalStateException("Invalid REST routes at " + configuration.location() + " [" + location + "]: " + reason);
    }
}
