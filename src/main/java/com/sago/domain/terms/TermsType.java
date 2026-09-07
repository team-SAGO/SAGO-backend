package com.sago.domain.terms;

/**
 * 동의받는 약관의 종류.
 *
 * 필수 여부와 현재 버전은 여기에 두지 않고 terms.yml에서 관리한다 —
 * 기획·법률 검토로 바뀔 때 코드 수정 없이 고칠 수 있어야 하기 때문이다.
 */
public enum TermsType {

    /** 서비스 이용약관 */
    SERVICE,

    /** 개인정보 수집·이용 동의 */
    PRIVACY,

    /** 위치기반서비스 이용 동의. 사고 위치(위경도)를 수집하므로 별도 항목으로 둔다. */
    LOCATION,

    /** 광고성 정보 수신 동의 */
    MARKETING
}
