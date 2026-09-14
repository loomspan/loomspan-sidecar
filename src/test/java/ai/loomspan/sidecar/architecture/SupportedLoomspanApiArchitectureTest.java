package ai.loomspan.sidecar.architecture;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

class SupportedLoomspanApiArchitectureTest
{
    private final JavaClasses sidecarClasses = new ClassFileImporter().importPackages("ai.loomspan.sidecar");

    @Test
    void sidecarCodeAndTestsDependOnlyOnSupportedLoomspanPackages()
    {
        assertThat(sidecarClasses.contain(LoomspanSidecarApplication.class)).isTrue();
        assertThat(sidecarClasses.contain(SupportedLoomspanApiArchitectureTest.class)).isTrue();

        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                        "ai.loomspan.internal..", "ai.loomspan.autoconfigure..")
                .check(sidecarClasses);
    }

    @Test
    void sidecarDoesNotDeclareJavaSkills()
    {
        noMethods().should().beAnnotatedWith(SkillMethod.class).check(sidecarClasses);
    }
}
