package org.mla.finance.web.dto;

/** Les deux écritures d'un transfert interne, liées par le même {@code transferGroup}. */
public record TransferResponse(String transferGroup, WithdrawalResponse outflow, ContributionResponse inflow) {
}
