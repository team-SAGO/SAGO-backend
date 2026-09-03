package com.sago.global.client.s3;

import java.util.Set;

/**
 * S3에 올라가는 파일의 종류. 종류마다 저장 경로·허용 확장자·용량 상한이 다르므로
 * 업로드 지점마다 검증 로직을 흩뿌리지 않고 이 enum 한 곳에서 관리한다.
 *
 * 용량 상한과 개수 상한은 기획안에 명시된 값이 없어 일반적인 모바일 업로드 기준으로 잡았다.
 * 실제 요금·정책이 정해지면 이 값만 조정하면 된다.
 */
public enum FileCategory {

    /** 음성 진술 원본 (Step 4). STT 변환 전 원본을 보존한다. */
    STATEMENT_AUDIO("statements/audio", 20 * 1024 * 1024L, 1,
        Set.of("mp3", "wav", "m4a", "aac", "flac", "webm", "ogg")),

    /** 사고 현장 사진. Vision 태깅의 입력이 된다. */
    ACCIDENT_PHOTO("accidents/photos", 10 * 1024 * 1024L, 10,
        Set.of("jpg", "jpeg", "png", "heic", "webp")),

    /** 보험증서·진단서 등 문서. OCR의 입력이 된다. */
    DOCUMENT("documents", 10 * 1024 * 1024L, 5,
        Set.of("jpg", "jpeg", "png", "heic", "webp", "pdf")),

    /** 프로필 이미지. */
    PROFILE_IMAGE("profiles", 5 * 1024 * 1024L, 1,
        Set.of("jpg", "jpeg", "png", "webp")),

    /** 생성된 AI 경위서 PDF. 사고 이력에서 재다운로드하기 위해 보관한다. */
    REPORT_PDF("reports", 20 * 1024 * 1024L, 1,
        Set.of("pdf"));

    private final String directory;
    private final long maxSizeBytes;
    private final int maxCount;
    private final Set<String> allowedExtensions;

    FileCategory(String directory, long maxSizeBytes, int maxCount, Set<String> allowedExtensions) {
        this.directory = directory;
        this.maxSizeBytes = maxSizeBytes;
        this.maxCount = maxCount;
        this.allowedExtensions = allowedExtensions;
    }

    public String getDirectory() {
        return directory;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    /**
     * 한 번에 올릴 수 있는 개수를 넘지 않는지 검사한다. 파일을 하나도 올리기 전에 확인해
     * 개수 초과로 중간에 실패하는 상황을 막는다.
     */
    public void validateCount(int count) {
        if (count <= 0) {
            throw new S3UploadException("업로드할 파일이 없습니다");
        }
        if (count > maxCount) {
            throw new S3UploadException(
                "한 번에 올릴 수 있는 파일은 " + maxCount + "개까지입니다 (요청 " + count + "개)");
        }
    }

    /**
     * 확장자·용량이 이 종류에 허용되는지 검사한다. 위반 시 업로드 전에 즉시 실패시킨다.
     */
    public void validate(String extension, long sizeBytes) {
        if (extension == null || !allowedExtensions.contains(extension)) {
            throw new S3UploadException(
                "허용되지 않은 파일 형식입니다: " + extension + " (허용: " + allowedExtensions + ")");
        }
        if (sizeBytes <= 0) {
            throw new S3UploadException("빈 파일은 업로드할 수 없습니다");
        }
        if (sizeBytes > maxSizeBytes) {
            throw new S3UploadException(
                "파일 용량이 너무 큽니다: " + sizeBytes + "바이트 (상한 " + maxSizeBytes + "바이트)");
        }
    }
}
