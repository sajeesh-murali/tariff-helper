package com.tariffhelper.api;

public final class ApiExceptions {

    private ApiExceptions() {
    }

    public static class AgreementNotFoundException extends RuntimeException {
        public AgreementNotFoundException(String agreementId) {
            super("Unknown agreement: " + agreementId);
        }
    }

    /** The agreement exists but its rules have not been added yet. */
    public static class AgreementNotAvailableException extends RuntimeException {
        public AgreementNotAvailableException(String agreementName, String reason) {
            super("Estimates for " + agreementName + " are not available yet."
                    + (reason == null || reason.isBlank() ? "" : " " + reason.strip()));
        }
    }

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
