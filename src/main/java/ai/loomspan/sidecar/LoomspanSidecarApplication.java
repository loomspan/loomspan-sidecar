package ai.loomspan.sidecar;

import ai.loomspan.sidecar.config.SidecarExecutionProperties;
import ai.loomspan.sidecar.config.RestRoutesProperties;
import ai.loomspan.sidecar.config.SidecarStorageProperties;
import ai.loomspan.sidecar.config.SidecarSnapshotProperties;
import ai.loomspan.sidecar.config.SidecarManagementProperties;
import ai.loomspan.sidecar.security.SidecarJwtProperties;
import ai.loomspan.sidecar.management.ManagementAdminCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ SidecarExecutionProperties.class, SidecarJwtProperties.class,
        RestRoutesProperties.class, SidecarStorageProperties.class, SidecarSnapshotProperties.class,
        SidecarManagementProperties.class })
public class LoomspanSidecarApplication
{
    public static void main(String[] args)
    {
        if (args.length > 0 && "admin".equals(args[0]))
        {
            System.exit(ManagementAdminCommand.run(args, System.out, System.err));
            return;
        }
        SpringApplication.run(LoomspanSidecarApplication.class, args);
    }
}
