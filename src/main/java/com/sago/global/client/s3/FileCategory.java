package com.sago.global.client.s3;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S3에 올라가는 파일의 종류. 종류마다 저장 경로·허용 확장자·용량 상한이 다르므로
 * 업로드 지점마다 검증 로직을 흩뿌리지 않고 이 enum 한 곳에서 관리한다.
 *
 * 용량 상한과 개수 상한은 기획안에 명시된 값이 없어 일반적인 모바일 업로드 기준으로 잡았다.
 * 실제 요금·정책이 정해지면 이 값만 조정하면 된다.
 *
 * 주의: 이 값들은 application.yml의 spring.servlet.multipart 설정과 함께 조정해야 한다.
 * (최대 용량 x 최대 개수)가 max-request-size를 넘으면 요청이 여기 오기 전에 잘려서
 * 아래 검증이 실행될 기회조차 없다.
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

    /**
     * 프로필 이미지.
     *
     * 사고 사진과 달리 heic를 허용하지 않는다. 프로필 이미지는 브라우저가 그대로 렌더링하는데
     * heic를 표시하지 못하는 브라우저가 많아, 받아두면 화면에서 깨진 이미지가 된다.
     * 아이폰 기본 촬영 포맷이 heic이므로 클라이언트가 변환해 보내야 한다.
     */
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
            throw new S3ValidationException("업로드할 파일 목록이 비어 있습니다");
        }
        if (count > maxCount) {
            throw new S3ValidationException(
                "한 번에 올릴 수 있는 파일은 " + maxCount + "개까지입니다 (요청 " + count + "개)");
        }
    }

    /**
     * 확장자·용량이 이 종류에 허용되는지 검사한다. 위반 시 업로드 전에 즉시 실패시킨다.
     */
    public void validate(String extension, long sizeBytes) {
        if (extension == null) {
            throw new S3ValidationException(
                "파일 확장자를 알 수 없습니다. 허용되는 형식: " + describeAllowedExtensions());
        }
        if (!allowedExtensions.contains(extension)) {
            throw new S3ValidationException(
                "허용되지 않은 파일 형식입니다: " + describeRejected(extension)
                    + " (허용: " + describeAllowedExtensions() + ")");
        }
        if (sizeBytes <= 0) {
            throw new S3ValidationException("빈 파일은 업로드할 수 없습니다");
        }
        if (sizeBytes > maxSizeBytes) {
            throw new S3ValidationException(
                "파일 용량이 너무 큽니다: " + describeSizeAtLeast(sizeBytes)
                    + " (상한 " + describeSize(maxSizeBytes) + ")");
        }
    }

    /** 거부된 확장자를 응답에 실을 때 남길 최대 길이. */
    private static final int REJECTED_EXTENSION_MAX_LENGTH = 20;

    /**
     * 거부된 확장자를 응답에 실을 수 있는 형태로 다듬는다.
     *
     * 이 값은 사용자가 지은 파일명의 마지막 점 뒤를 그대로 잘라낸 것이라 무엇이든 들어올 수 있다.
     * 검증 실패가 400으로 내려가면서 응답 본문에 실리므로, 두 가지를 막는다.
     * - 파일명이 길면 에러 메시지가 통째로 길어진다
     * - 파일명에 넣은 태그·따옴표가 그대로 되돌아간다 (프론트가 이스케이프하지 않으면 그대로 그려진다)
     */
    private static String describeRejected(String extension) {
        String safe = extension.replaceAll("[^a-z0-9]", "");
        if (safe.isEmpty()) {
            return "(알 수 없음)";
        }
        return safe.length() > REJECTED_EXTENSION_MAX_LENGTH
            ? safe.substring(0, REJECTED_EXTENSION_MAX_LENGTH) + "…"
            : safe;
    }

    /**
     * 허용 확장자를 사용자에게 보여줄 형태로 만든다.
     * Set의 기본 문자열은 순서가 정해져 있지 않아 실행할 때마다 달라지므로 정렬한다.
     */
    private String describeAllowedExtensions() {
        return allowedExtensions.stream().sorted().collect(Collectors.joining(", "));
    }

    /**
     * 용량을 MB로 표기한다. 바이트 숫자를 그대로 보여주면 얼마나 줄여야 하는지 알기 어렵다.
     */
    private static String describeSize(long bytes) {
        return trimZero(bytes / (double) (1024 * 1024)) + "MB";
    }

    /**
     * 상한을 넘은 실제 용량 표기. 소수점 첫째 자리에서 올린다.
     * 내림하면 상한을 1바이트 넘긴 파일이 "5MB (상한 5MB)"로 나와, 왜 거부됐는지 알 수 없다.
     */
    private static String describeSizeAtLeast(long bytes) {
        double megabytes = bytes / (double) (1024 * 1024);
        return trimZero(Math.ceil(megabytes * 10) / 10) + "MB";
    }

    private static String trimZero(double megabytes) {
        String text = String.format(Locale.ROOT, "%.1f", megabytes);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
