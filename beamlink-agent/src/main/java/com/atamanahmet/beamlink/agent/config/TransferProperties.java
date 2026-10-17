package com.atamanahmet.beamlink.agent.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "beamlink.transfer")
@Validated
@Getter
@Setter
public class TransferProperties {

    @Min(65_536)
    @Max(8_388_608)
    private int chunkSizeBytes = 524_288;

    @Min(1)
    @Max(16)
    private int windowSize = 1;
}
