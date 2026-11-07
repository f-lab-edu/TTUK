package com.ttuk.support;

import com.ttuk.global.config.S3Properties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    private static final String LOCALSTACK_IMAGE = "localstack/localstack:2026.10.0";
    private static final String LOCALSTACK_TOKEN_ENV = "LOCALSTACK_AUTH_TOKEN";

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4"))
                .withExposedPorts(6379);
    }

    @Bean
    LocalStackContainer localStackContainer() {
        String token = System.getenv(LOCALSTACK_TOKEN_ENV);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    LOCALSTACK_TOKEN_ENV + " 환경 변수가 없습니다. LocalStack 토큰을 setx로 등록하고 IntelliJ를 재시작하세요.");
        }
        return new LocalStackContainer(DockerImageName.parse(LOCALSTACK_IMAGE))
                .withEnv(LOCALSTACK_TOKEN_ENV, token);
    }

    @Bean
    @Primary
    S3Client localStackS3Client(LocalStackContainer localStack) {
        return S3Client.builder()
                .region(Region.of(localStack.getRegion()))
                .endpointOverride(localStack.getEndpoint())
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .build();
    }

    @Bean
    SmartInitializingSingleton createTestBucket(S3Client s3Client, S3Properties properties) {
        return () -> s3Client.createBucket(builder -> builder.bucket(properties.bucket()));
    }
}