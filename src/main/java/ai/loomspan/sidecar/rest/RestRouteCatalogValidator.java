package ai.loomspan.sidecar.rest;

import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillKind;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
final class RestRouteCatalogValidator implements SmartInitializingSingleton {
    private final RestRouteConfiguration configuration;
    private final SkillCatalog catalog;

    RestRouteCatalogValidator(RestRouteLoader loader, SkillCatalog catalog) {
        this.configuration = loader.configuration();
        this.catalog = catalog;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> restSkills = new LinkedHashSet<>();
        catalog.skills().forEach(descriptor -> {
            if (descriptor.kind() == SkillKind.REST) restSkills.add(descriptor.name());
        });
        for (String skill : restSkills) {
            if (!configuration.routes().containsKey(skill)) fail("REST skill '" + skill + "' has no route");
        }
        configuration.routes().forEach((name, route) -> {
            var descriptor = catalog.skill(name).orElseThrow(() -> failure("route '" + name + "' names an unknown skill"));
            if (descriptor.kind() != SkillKind.REST) fail("route '" + name + "' names a non-REST skill");
            if (!configuration.targets().containsKey(route.target())) fail("route '" + name + "' names an unknown target");
        });
    }

    private void fail(String reason) { throw failure(reason); }
    private IllegalStateException failure(String reason) {
        return new IllegalStateException("Invalid REST routes at " + configuration.location() + ": " + reason);
    }
}
