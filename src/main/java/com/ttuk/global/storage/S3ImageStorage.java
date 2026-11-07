package com.ttuk.global.storage;

import com.ttuk.global.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ImageStorage {

    private static final String KEY_PREFIX = "images/";
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final S3Properties properties;

    public String upload(MultipartFile file, ImageType type) {
        String key = KEY_PREFIX + UUID.randomUUID() + "." + type.getExtension();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(type.getContentType())
                .cacheControl(CACHE_CONTROL)
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("업로드할 이미지를 읽지 못했습니다.", e);
        }
        return key;
    }

    public void deleteAllQuietly(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }

        List<ObjectIdentifier> objects = new ArrayList<>(keys.size());
        for (String key : keys) {
            objects.add(ObjectIdentifier.builder().key(key).build());
        }

        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(properties.bucket())
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .build();

        try {
            DeleteObjectsResponse response = s3Client.deleteObjects(request);
            if (response.hasErrors()) {
                log.warn("S3 이미지 일부 삭제 실패 keys={} errors={}", keys, response.errors());
            }
        } catch (SdkException e) {
            log.warn("S3 이미지 삭제 실패 keys={}", keys, e);
        }
    }

    public String toUrl(String key) {
        return properties.publicBaseUrl() + "/" + key;
    }
}