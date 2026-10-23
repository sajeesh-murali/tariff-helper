package com.tariffhelper.api;

import java.util.List;

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

    /** No agreementId was given and none could be suggested from the employer name. */
    public static class AgreementNotDeterminedException extends RuntimeException {
        private final List<String> availableAgreements;

        public AgreementNotDeterminedException(List<String> availableAgreements) {
            super("Could not determine the agreement from the employer name. Choose one of the available "
                    + "agreements: " + String.join(", ", availableAgreements) + ".");
            this.availableAgreements = List.copyOf(availableAgreements);
        }

        public List<String> availableAgreements() {
            return availableAgreements;
        }
    }

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
