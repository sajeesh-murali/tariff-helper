package com.tariffhelper.domain;

import java.math.BigDecimal;

/**
 * Seniority the employer already credits, read from payslip values (calculated in code).
 *
 * @param creditedYears      null when no comparison was possible
 * @param basis              "lonnstrinn" or "ansiennitetsdato"; null when creditedYears is null
 * @param stepNotOnLadder    a lønnstrinn was given but it is not on this position's ladder
 * @param stepNumbersMissing a lønnstrinn was given but the agreement has no step numbers, so it was not used
 */
public record CreditedSeniority(BigDecimal creditedYears, String basis,
                                boolean stepNotOnLadder, boolean stepNumbersMissing) {

    public static final String BASIS_LONNSTRINN = "lonnstrinn";
    public static final String BASIS_ANSIENNITETSDATO = "ansiennitetsdato";

    public static final CreditedSeniority NONE = new CreditedSeniority(null, null, false, false);

    public boolean compared() {
        return creditedYears != null;
    }
}
