package com.sago.domain.checklist;

/** 존재하지 않는 체크리스트 항목이거나, 지정한 사고의 항목이 아닌 경우에 던진다. */
public class ChecklistItemNotFoundException extends RuntimeException {

    public ChecklistItemNotFoundException(String message) {
        super(message);
    }
}
