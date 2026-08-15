package org.frostnova.aigateway.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private boolean registrationEnabled = true;
    private Duration sessionTtl = Duration.ofHours(12);
}
