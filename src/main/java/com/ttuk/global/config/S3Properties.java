package com.ttuk.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ttuk.s3")
public record S3Properties(

        @NotBlank
        String bucket,
        @NotBlank
        String region,
        @NotBlank
        String publicBaseUrl


) {
}
