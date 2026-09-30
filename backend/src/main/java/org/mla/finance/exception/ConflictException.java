package org.mla.finance.exception;

/** L'état actuel de la ressource interdit l'opération (ex. contribution déjà annulée), traduite en HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
