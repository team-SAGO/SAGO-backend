package com.sago.global.client.s3;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.UUID;

/**
 * S3 파일 업로드 공통 모듈. 음성 진술·사고 사진·문서·프로필 이미지·경위서 PDF가
 * 모두 이 클래스를 거쳐 저장되고, 저장된 URL을 각 엔티티가 문자열로 보관한다.
 *
 * 파일명은 UUID로 새로 만든다. 원본 파일명을 그대로 쓰면 한글·공백 때문에 URL이 깨지고
 * 같은 이름의 파일끼리 덮어쓰기가 발생하기 때문이다.
 *
 * 버킷은 비공개를 전제로 하며, 조회용 presigned URL 발급은 다운로드 기능 붙일 때 추가한다.
 */
@Component
public class S3Uploader {

    private final S3Client s3Client;
    private final S3Properties properties;

    public S3Uploader(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * 클라이언트가 올린 파일을 업로드하고 저장된 URL을 돌려준다.
     */
    public String upload(MultipartFile file, FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw new S3UploadException("업로드할 파일이 없습니다");
        }

        String extension = extractExtension(file.getOriginalFilename());
        category.validate(extension, file.getSize());

        try {
            return put(file.getBytes(), extension, resolveContentType(file.getContentType(), extension), category);
        } catch (IOException e) {
            throw new S3UploadException("업로드 파일을 읽지 못했습니다", e);
        }
    }

    /**
     * 서버가 메모리에서 생성한 파일(경위서 PDF 등)을 업로드하고 저장된 URL을 돌려준다.
     */
    public String upload(byte[] bytes, String extension, FileCategory category) {
        if (bytes == null || bytes.length == 0) {
            throw new S3UploadException("업로드할 파일이 없습니다");
        }

        String normalized = normalize(extension);
        category.validate(normalized, bytes.length);

        return put(bytes, normalized, resolveContentType(null, normalized), category);
    }

    /**
     * 업로드된 파일을 삭제한다. 이미 없는 객체를 지워도 S3는 성공으로 응답한다.
     */
    public void delete(String fileUrl) {
        String key = extractKey(fileUrl);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build());
        } catch (Exception e) {
            throw new S3UploadException("S3 파일 삭제 실패: " + key, e);
        }
    }

    private String put(byte[] bytes, String extension, String contentType, FileCategory category) {
        String key = category.getDirectory() + "/" + UUID.randomUUID() + "." + extension;

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .build(),
                RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            throw new S3UploadException("S3 업로드 실패: " + key, e);
        }

        return toUrl(key);
    }

    private String toUrl(String key) {
        return "https://" + properties.getBucket() + ".s3." + properties.getRegion() + ".amazonaws.com/" + key;
    }

    /**
     * 저장된 URL에서 S3 오브젝트 키를 되돌린다. 삭제·재다운로드 때 필요하다.
     */
    public String extractKey(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new S3UploadException("파일 URL이 비어 있습니다");
        }
        try {
            String path = new URI(fileUrl).getPath();
            if (path == null || path.length() <= 1) {
                throw new S3UploadException("S3 파일 URL이 아닙니다: " + fileUrl);
            }
            return path.substring(1);
        } catch (URISyntaxException e) {
            throw new S3UploadException("잘못된 파일 URL입니다: " + fileUrl, e);
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return null;
        }
        return normalize(originalFilename.substring(dot + 1));
    }

    private String normalize(String extension) {
        return extension == null ? null : extension.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 브라우저가 준 Content-Type을 우선 쓰고, 없거나 무의미한 값이면 확장자로 추론한다.
     * 잘못된 Content-Type으로 저장하면 재다운로드 때 파일이 열리지 않는다.
     */
    private String resolveContentType(String contentType, String extension) {
        if (contentType != null && !contentType.isBlank()
            && !contentType.equals("application/octet-stream")) {
            return contentType;
        }
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "pdf" -> "application/pdf";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "m4a", "aac" -> "audio/mp4";
            case "flac" -> "audio/flac";
            case "ogg" -> "audio/ogg";
            case "webm" -> "audio/webm";
            default -> "application/octet-stream";
        };
    }
}
