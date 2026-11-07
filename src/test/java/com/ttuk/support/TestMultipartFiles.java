package com.ttuk.support;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

public final class TestMultipartFiles {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int FAKE_IMAGE_SIZE = 64;

    private TestMultipartFiles() {
    }

    public static MockMultipartFile requestPart(String json) {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    public static MockMultipartFile pngImage(String filename) {
        byte[] content = new byte[FAKE_IMAGE_SIZE];
        System.arraycopy(PNG_SIGNATURE, 0, content, 0, PNG_SIGNATURE.length);
        return new MockMultipartFile("images", filename, MediaType.IMAGE_PNG_VALUE, content);
    }

    public static MockMultipartFile fakePngImage(String filename) {
        return new MockMultipartFile("images", filename, MediaType.IMAGE_PNG_VALUE,
                "this is not an image".getBytes(StandardCharsets.UTF_8));
    }
}