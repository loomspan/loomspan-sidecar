package ai.loomspan.sidecar;

import ai.loomspan.sidecar.config.SidecarExecutionProperties;
import ai.loomspan.sidecar.security.SidecarJwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ SidecarExecutionProperties.class, SidecarJwtProperties.class })
public class LoomspanSidecarApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(LoomspanSidecarApplication.class, args);
    }
}
