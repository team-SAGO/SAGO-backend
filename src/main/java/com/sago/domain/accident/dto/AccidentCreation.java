package com.sago.domain.accident.dto;

/**
 * 사고 시작 결과.
 *
 * @param accident 새로 만들었거나 이어서 진행할 사고
 * @param created  새로 만들었으면 true, 진행 중이던 사고를 돌려줬으면 false.
 *                 클라이언트가 "이어서 진행합니다"를 안내할 수 있도록 응답 코드(201/200)로 구분한다.
 */
public record AccidentCreation(AccidentResponse accident, boolean created) {

    public static AccidentCreation created(AccidentResponse accident) {
        return new AccidentCreation(accident, true);
    }

    public static AccidentCreation resumed(AccidentResponse accident) {
        return new AccidentCreation(accident, false);
    }
}
