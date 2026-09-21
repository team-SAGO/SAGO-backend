package com.sago.domain.report;

/** 확정된 경위서를 고치거나 다시 만들려 한 경우. */
public class ReportAlreadyConfirmedException extends RuntimeException {

    public ReportAlreadyConfirmedException(String message) {
        super(message);
    }
}
