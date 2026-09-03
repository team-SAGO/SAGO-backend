package com.sago.global.client.s3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * S3 클라이언트 빈 설정.
 * .env에 액세스 키가 있으면 그 값을 쓰고, 둘 다 비어 있으면 SDK 기본 체인
 * (환경변수 → ~/.aws/credentials → IAM 역할)으로 넘긴다.
 * 키가 없어도 빈 생성 자체는 성공하므로 로컬에서 AWS 없이 앱을 띄울 수 있다.
 *
 * 액세스 키와 시크릿 키 중 하나만 설정된 경우는 오타로 보고 기동을 실패시킨다.
 * 조용히 기본 체인으로 넘어가면 의도한 것과 다른 AWS 계정으로 업로드될 수 있기 때문이다.
 */
@Configuration
public class S3Config {

    private final S3Properties properties;

    public S3Config(S3Properties properties) {
        this.properties = properties;
    }

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
            .region(Region.of(properties.getRegion()));

        boolean hasAccessKey = hasText(properties.getAccessKey());
        boolean hasSecretKey = hasText(properties.getSecretKey());

        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException(
                "AWS_ACCESS_KEY_ID와 AWS_SECRET_ACCESS_KEY는 함께 설정해야 합니다. "
                    + "기본 자격증명 체인을 쓰려면 둘 다 비워두세요.");
        }

        if (hasAccessKey) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
