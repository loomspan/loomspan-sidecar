package ai.loomspan.sidecar.execution;

import ai.loomspan.api.AdmittedSkillInvocation;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.api.SkillTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.function.Consumer;

final class TestSkillInvocationHandoff {
    private TestSkillInvocationHandoff() { }

    static SkillInvocationHandoff from(SkillTemplate template) {
        return new SkillInvocationHandoff() {
            @Override
            public AdmittedSkillInvocation handoff(String skillName, Object input) {
                return admitted(() -> template.invoke(skillName, input),
                        observer -> template.invoke(skillName, input, observer));
            }

            @Override
            public AdmittedSkillInvocation handoff(String skillName, Map<String, Object> input) {
                return admitted(() -> template.invoke(skillName, input),
                        observer -> template.invoke(skillName, input, observer));
            }
        };
    }

    private static AdmittedSkillInvocation admitted(
            java.util.function.Supplier<String> invocation,
            java.util.function.Function<Consumer<SkillExecutionView>, String> observedInvocation) {
        var captured = SecurityContextHolder.getContext().getAuthentication();
        return new AdmittedSkillInvocation() {
            @Override public String generationId() { return "test-generation"; }
            @Override public String invoke() { return withCapturedAuthentication(invocation); }
            @Override public String invoke(Consumer<SkillExecutionView> observer) {
                return withCapturedAuthentication(() -> observedInvocation.apply(observer));
            }
            @Override public void release() { }

            private String withCapturedAuthentication(java.util.function.Supplier<String> action) {
                var previous = SecurityContextHolder.getContext();
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(captured);
                SecurityContextHolder.setContext(context);
                try {
                    return action.get();
                } finally {
                    SecurityContextHolder.setContext(previous);
                }
            }
        };
    }
}
