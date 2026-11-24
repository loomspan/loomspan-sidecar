package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.management.ManagementEditingService;
import ai.loomspan.sidecar.management.ManagementUserDetailsService;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "sidecar.fixtureSuite", matches = ".+")
class ExternalConfigurationFixturesIntegrationTest {
    @TempDir Path directory;

    @Test void actualConsumerPacksBootInFileModeAndValidatePublishAndRestoreInManagedMode() throws Exception {
        Path fixture = Path.of(System.getProperty("sidecar.fixtureSuite")).resolve("config");
        var packs = List.of(fixture.resolve("skills/equipment-service"), fixture.resolve("skills/typesafe-ai"),
                fixture.resolve("executor-skills/equipment-service"));
        var documents = new ArrayList<SkillDocument>();
        for (Path pack : packs) {
            try (var paths = Files.list(pack)) {
                for (Path path : paths.filter(item -> item.toString().endsWith(".yaml")).sorted().toList())
                    documents.add(new SkillDocument(path.getFileName().toString(), Files.readString(path)));
            }
        }
        var mapper = new YAMLMapper();
        Set<String> expected = documents.stream().map(document -> "default:" + mapper.readTree(document.yaml())
                .path("name").asText()).collect(Collectors.toSet());
        assertThat(expected).hasSize(documents.size()).contains("default:asset-context", "default:ts-triage-fixed");
        String authored = Files.readString(fixture.resolve("sidecar.yaml"))
                .replace("http://fixtures:8080", "http://127.0.0.1:9")
                .replace("http://python:8080", "http://127.0.0.1:9");
        Path startup = directory.resolve("fixture-sidecar.yml");
        Files.writeString(startup, authored);
        String locations = packs.stream().map(path -> path.toUri() + "*.yaml").collect(Collectors.joining(","));
        try (var file = start("file", "--loomspan-sidecar.configuration.mode=file",
                "--spring.config.additional-location=" + startup.toUri(), "--loomspan.skills.locations=" + locations)) {
            assertThat(skills(file)).isEqualTo(expected);
        }

        var loomspan = (ObjectNode) mapper.readTree(authored).path("loomspan");
        loomspan.remove("observability");
        loomspan.remove("skills");
        String execution = mapper.writeValueAsString(mapper.createObjectNode().set("loomspan", loomspan));
        try (var managed = start("managed")) {
            String email = "pack-editor@fixture.test";
            managed.getBean(JdbcTemplate.class).update(
                    "INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,'editor',1,'fixture',?)",
                    email, System.currentTimeMillis());
            var user = (ManagementUserDetailsService.Principal)
                    managed.getBean(ManagementUserDetailsService.class).loadUserByUsername(email);
            var session = new MockHttpSession();
            session.setAttribute("management.activity", System.currentTimeMillis());
            var editing = managed.getBean(ManagementEditingService.class);
            var grant = editing.acquire(session, user, "External fixture", false, false);
            var draft = grant.draft();
            draft = editing.save(session, user, grant.editingSessionId(), grant.generation(), draft.draftId(),
                    draft.revision(), draft.baseSnapshotId(), new ManagedConfiguration(documents, execution));
            var validation = editing.validate(session, user, grant.editingSessionId(), grant.generation(), draft.draftId(),
                    draft.revision(), draft.baseSnapshotId()).validation();
            assertThat(validation.successful()).as(validation.toString()).isTrue();
            assertThat(skills(managed)).isEmpty();
            var published = editing.publish(session, user, grant.editingSessionId(), grant.generation(), draft.draftId(),
                    draft.revision(), draft.baseSnapshotId());
            assertThat(published.configuration().skillDocuments()).isEqualTo(documents);
            assertThat(skills(managed)).isEqualTo(expected);
        }
        try (var restored = start("managed")) {
            assertThat(skills(restored)).isEqualTo(expected);
            assertThat(restored.getBean(RuntimeConfigurationService.class).publishedSnapshot()
                    .configuration().skillDocuments()).isEqualTo(documents);
        }
    }

    private Set<String> skills(ConfigurableApplicationContext context) {
        return context.getBean(SkillReloader.class).snapshot("default").skills().stream()
                .map(skill -> skill.qualifiedName()).collect(Collectors.toSet());
    }

    private ConfigurableApplicationContext start(String name, String... extra) {
        var args = new ArrayList<>(List.of("--loomspan-sidecar.storage.database-path=" + directory.resolve(name + ".db"),
                "--spring.profiles.active=typesafe", "--spring.main.banner-mode=off", "--logging.level.root=ERROR", "--loomspan.observability.enabled=false",
                "--loomspan.observability.auth.api-key=offline-fixture", "--loomspan-sidecar.trust.mode=file",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.trust.file.jwks=src/test/resources/fixtures/jwks.json"));
        args.addAll(List.of(extra));
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE).run(args.toArray(String[]::new));
    }

}
