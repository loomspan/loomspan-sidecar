package ai.loomspan.sidecar.support;

/** Child-process probe uses production environment key provisioning, never a test cipher bean. */
public final class ConfigurationStartupProbe {
    public static void main(String[] args) {
        try (var context = new org.springframework.boot.builder.SpringApplicationBuilder(
                ai.loomspan.sidecar.LoomspanSidecarApplication.class)
                .web(org.springframework.boot.WebApplicationType.NONE).run(args)) {
            System.out.println("PROBE_READY:" + context.getBean(
                    ai.loomspan.sidecar.configuration.RuntimeConfigurationService.class).mode().value());
        }
    }
}
