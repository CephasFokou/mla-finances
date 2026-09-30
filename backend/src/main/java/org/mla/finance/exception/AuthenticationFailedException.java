package org.mla.finance.exception;

/**
 * Échec d'authentification RG-18 : identifiants incorrects, compte non actif, ou jeton
 * d'activation/réinitialisation invalide ou expiré. Traduite en HTTP 401. Volontairement générique
 * (le message ne distingue jamais "email inconnu" de "mot de passe incorrect") pour ne pas
 * permettre l'énumération des comptes existants.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
