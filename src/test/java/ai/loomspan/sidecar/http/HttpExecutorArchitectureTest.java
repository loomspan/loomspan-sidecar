package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.assertj.core.api.Assertions.*;

class HttpExecutorArchitectureTest {
    @Test void runtimeAndTestsUseOnlySupportedFrameworkTypes() {
        var classes=new ClassFileImporter().importPackages("ai.loomspan.sidecar.http","ai.loomspan.sidecar.configuration","ai.loomspan.sidecar.storage");
        assertThat(classes.contain(HttpSkillExecutor.class)).isTrue();assertThat(classes.contain(HttpExecutorArchitectureTest.class)).isTrue();
        noClasses().should().dependOnClassesThat().resideInAnyPackage("ai.loomspan.internal..","ai.loomspan.autoconfigure..").check(classes);
        noMethods().should().beAnnotatedWith(SkillMethod.class).check(classes);
    }
}
