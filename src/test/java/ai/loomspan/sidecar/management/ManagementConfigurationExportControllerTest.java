package ai.loomspan.sidecar.management;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManagementConfigurationExportControllerTest {
    @Test void missingRuntimeExplainsExportUnavailability() throws Exception {
        var runtime = mock(RuntimeConfigurationService.class);
        when(runtime.publishedSnapshot()).thenThrow(new IllegalStateException("unavailable"));
        var mvc = MockMvcBuilders.standaloneSetup(new ManagementConfigurationController(
                runtime, mock(ManagementEditingService.class), mock(ManagementConfigurationImportService.class),
                mock(ManagementConfigurationRollbackService.class))).build();
        mvc.perform(get("/api/management/configuration/export"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("export_unavailable"))
                .andExpect(jsonPath("$.error").value("No runtime-published configuration is available for export"));
    }

    @Test void excessiveEntryCountFailsBeforeDownload() throws Exception {
        var runtime = mock(RuntimeConfigurationService.class);
        var skills = new ArrayList<SkillDocument>();
        for (int i = 0; i < 9999; i++) skills.add(new SkillDocument("skill-" + i, "name: example\n"));
        when(runtime.publishedSnapshot()).thenReturn(new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                new ManagedConfiguration(skills, "targets: {}\nroutes: {}\n"), SnapshotStatus.PUBLISHED));
        var mvc = MockMvcBuilders.standaloneSetup(new ManagementConfigurationController(
                runtime, mock(ManagementEditingService.class), mock(ManagementConfigurationImportService.class),
                mock(ManagementConfigurationRollbackService.class))).build();
        mvc.perform(get("/api/management/configuration/export"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("export_too_large"));
    }
}
