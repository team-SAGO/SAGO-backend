package com.sago.global.client.s3;

import java.util.List;

/**
 * 여러 파일을 올린 결과. 성공한 URL과 실패한 파일을 함께 담는다.
 *
 * 사고 현장 사진처럼 부분 성공을 허용하는 업로드에서 쓴다. 예외를 던져버리면 호출자는
 * 몇 장이 성공했는지조차 알 수 없어, 성공분을 저장할 수도 사용자에게 안내할 수도 없다.
 *
 * @param uploadedUrls 성공한 파일의 저장 주소. 요청 순서를 유지한다.
 * @param failures     실패한 파일. 전부 성공했다면 비어 있다.
 */
public record MultiUploadResult(List<String> uploadedUrls, List<FailedUpload> failures) {

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    /**
     * 실패한 파일 하나.
     *
     * 파일명과 순번을 함께 담는다. 파일명만으로는 같은 이름이 여러 장일 때 구분되지 않고,
     * 순번만으로는 재시도 화면에서 목록이 바뀌면 어긋난다.
     *
     * @param index    요청에서의 순번(0부터)
     * @param filename 원본 파일명. 없으면 null이다.
     * @param type     사용자가 파일을 고쳐야 하는지, 다시 시도하면 되는지
     * @param message  사용자에게 보여줄 안내
     */
    public record FailedUpload(int index, String filename, FailureType type, String message) {
    }

    /**
     * 실패 원인의 구분. 안내 문구와 사용자가 할 행동이 달라지므로 나눈다.
     */
    public enum FailureType {
        /** 확장자·용량이 조건에 맞지 않는다. 그 파일을 바꿔야 한다. */
        INVALID_FILE,
        /** 저장소 통신에 실패했다. 같은 파일로 다시 시도하면 된다. */
        STORAGE_ERROR
    }
}
