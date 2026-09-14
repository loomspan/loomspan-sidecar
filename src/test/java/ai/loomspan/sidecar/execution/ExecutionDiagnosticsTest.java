package ai.loomspan.sidecar.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import ai.loomspan.api.SkillExecutionEvent;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillException;
import ai.loomspan.api.SkillInputValidationException;
import ai.loomspan.api.SkillInputValidationIssue;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.sidecar.config.SidecarExecutionProperties;
import ai.loomspan.sidecar.security.ExecutionOwner;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionDiagnosticsTest {
    @Test
    void classifiesOnlyThePrimaryFacadeFailure() {
        var issue = new SkillInputValidationIssue("$.message", "required", "Message is required");
        var validation = ExecutionFailureClassifier.classify(
                new SkillInputValidationException("invalid input", List.of(issue)));
        assertThat(validation.kind()).isEqualTo(ExecutionFailure.Kind.INPUT_VALIDATION);
        assertThat(validation.issues()).containsExactly(issue);

        assertThat(ExecutionFailureClassifier.classify(new AccessDeniedException("denied")).kind())
                .isEqualTo(ExecutionFailure.Kind.ACCESS_DENIED);
        assertThat(ExecutionFailureClassifier.classify(
                new SkillException("outer", new AccessDeniedException("nested"))).kind())
                .isEqualTo(ExecutionFailure.Kind.SKILL_FAILURE);
    }

    @Test
    void publishesSelectedEventsAtomicallyWithPrimaryFailure() throws Exception {
        SkillTemplate template = mock(SkillTemplate.class);
        var event = new SkillExecutionEvent(Instant.now(), "ERROR", "skill.failed",
                Map.of("business", "visible"), "frame", "root");
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var observer = (java.util.function.Consumer<SkillExecutionView>) invocation.getArgument(2);
            observer.accept(new SkillExecutionView("session", List.of(event)));
            throw new SkillException("primary facade message");
        });
        var properties = new SidecarExecutionProperties();
        properties.setDiagnostics(SidecarExecutionProperties.Diagnostics.ONERROR);
        var coordinator = new ExecutionCoordinator(template, properties, mock(ApplicationContext.class));
        var authentication = authentication();
        var owner = ExecutionOwner.from(authentication);
        try {
            var id = coordinator.admit("skill", Map.of(), 2, owner, authentication);
            ExecutionSnapshot snapshot = terminal(coordinator, id, owner);
            assertThat(snapshot.status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(snapshot.failure().kind()).isEqualTo(ExecutionFailure.Kind.SKILL_FAILURE);
            assertThat(snapshot.failure().message()).isEqualTo("primary facade message");
            assertThat(snapshot.events()).containsExactly(event);
        } finally {
            coordinator.destroy();
        }
    }

    @Test
    void appliesNeverOnErrorAndAlwaysSelectionToTerminalOutcomes() throws Exception {
        assertThat(run(SidecarExecutionProperties.Diagnostics.NEVER, false).events()).isNull();
        assertThat(run(SidecarExecutionProperties.Diagnostics.NEVER, true).events()).isNull();
        assertThat(run(SidecarExecutionProperties.Diagnostics.ONERROR, false).events()).isNull();
        assertThat(run(SidecarExecutionProperties.Diagnostics.ONERROR, true).events()).hasSize(1);
        assertThat(run(SidecarExecutionProperties.Diagnostics.ALWAYS, false).events()).hasSize(1);
        assertThat(run(SidecarExecutionProperties.Diagnostics.ALWAYS, true).events()).hasSize(1);
    }

    @Test
    void failureWithoutObserverCallbackFinishesWithoutInventedEvents() throws Exception {
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenThrow(new SkillException("no callback"));
        var properties = new SidecarExecutionProperties();
        properties.setDiagnostics(SidecarExecutionProperties.Diagnostics.ALWAYS);
        var coordinator = new ExecutionCoordinator(template, properties, mock(ApplicationContext.class));
        var authentication = authentication();
        var owner = ExecutionOwner.from(authentication);
        try {
            var snapshot = terminal(coordinator,
                    coordinator.admit("skill", Map.of(), 2, owner, authentication), owner);
            assertThat(snapshot.failure().message()).isEqualTo("no callback");
            assertThat(snapshot.events()).isEmpty();
        } finally {
            coordinator.destroy();
        }
    }

    private ExecutionSnapshot run(SidecarExecutionProperties.Diagnostics diagnostics, boolean fail) throws Exception {
        SkillTemplate template = mock(SkillTemplate.class);
        var event = new SkillExecutionEvent(Instant.now(), "INFO", "fixture.event", Map.of(), null, null);
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var observer = (java.util.function.Consumer<SkillExecutionView>) invocation.getArgument(2);
            observer.accept(new SkillExecutionView("session", List.of(event)));
            if (fail) throw new SkillException("failure");
            return "success";
        });
        var properties = new SidecarExecutionProperties();
        properties.setDiagnostics(diagnostics);
        var coordinator = new ExecutionCoordinator(template, properties, mock(ApplicationContext.class));
        var authentication = authentication();
        var owner = ExecutionOwner.from(authentication);
        try {
            return terminal(coordinator, coordinator.admit("skill", Map.of(), 2, owner, authentication), owner);
        } finally {
            coordinator.destroy();
        }
    }

    private ExecutionSnapshot terminal(ExecutionCoordinator coordinator, java.util.UUID id,
            ExecutionOwner owner) throws Exception {
        for (int count = 0; count < 100; count++) {
            var snapshot = coordinator.find(id, owner).orElseThrow();
            if (snapshot.completedAt() != null) return snapshot;
            TimeUnit.MILLISECONDS.sleep(5);
        }
        throw new AssertionError("execution did not finish");
    }

    private JwtAuthenticationToken authentication() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none")
                .issuer("https://issuer.test").subject("owner")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
