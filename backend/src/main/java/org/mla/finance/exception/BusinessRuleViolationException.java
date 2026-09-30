package org.mla.finance.exception;

import lombok.Getter;

/** Violation d'une règle de gestion du Cahier des Charges (ex. RG-02), traduite en HTTP 422. */
@Getter
public class BusinessRuleViolationException extends RuntimeException {

    private final String rule;

    public BusinessRuleViolationException(String rule, String message) {
        super(message);
        this.rule = rule;
    }
}
