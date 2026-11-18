package org.mla.finance.exception;

import lombok.Getter;

/**
 * Requête incomplète ou mal formée qu'une annotation de validation ne peut pas exprimer (champ
 * requis selon le type de don, par exemple). Traduite en HTTP 400, avec la même forme de corps
 * qu'une erreur de validation : {@code { errors: [{ field, message }] }}.
 */
@Getter
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }
}
