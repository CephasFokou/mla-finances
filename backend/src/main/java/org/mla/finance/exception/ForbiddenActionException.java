package org.mla.finance.exception;

/**
 * L'utilisateur a la permission générale mais pas le droit de faire cette action sur cette ressource
 * (ex. valider un retrait qu'il a lui-même saisi), traduite en HTTP 403 avec un message lisible.
 */
public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
