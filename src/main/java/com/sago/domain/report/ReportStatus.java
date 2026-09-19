package com.sago.domain.report;

/**
 * 경위서의 상태. 생성 직후에는 항상 DRAFT이고, 사용자가 검토를 마치고 확정하면 CONFIRMED가 된다.
 */
public enum ReportStatus {
    DRAFT,
    CONFIRMED
}
