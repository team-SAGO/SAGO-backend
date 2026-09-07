package com.sago.domain.user.dto;

import com.sago.domain.user.User;

/**
 * 프로필 조회 응답.
 *
 * @param userId      회원 식별자
 * @param email       가입에 사용된 이메일. 소셜에서 이메일 동의를 받지 못했다면 null이다 —
 *                    내부적으로 채워둔 자리표시 주소는 실재하지 않으므로 그대로 내보내지 않는다.
 * @param nickname    닉네임. 초기 설정 전에는 소셜에서 받아온 값이거나 null이다.
 * @param bikeModel   이륜차 차종
 * @param bikeNumber  이륜차 번호
 * @param profileSet  초기 프로필 설정을 마쳤는지 여부. 클라이언트가 온보딩으로 보낼지 판단하는 데 쓴다.
 */
public record ProfileResponse(
    Long userId,
    String email,
    String nickname,
    String bikeModel,
    String bikeNumber,
    boolean profileSet
) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(
            user.getUserId(),
            // 자리표시 주소를 그대로 보내면 사용자가 자기 이메일로 오해한다.
            user.hasPlaceholderEmail() ? null : user.getEmail(),
            user.getNickname(),
            user.getBikeModel(),
            user.getBikeNumber(),
            user.isProfileSet()
        );
    }
}
