package org.mla.finance.web.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Seuls donorId, anonymous et notes sont modifiables (remplacés tels quels : donorId/notes null les
 * effacent, anonymous null vaut false). Les champs financiers sont acceptés uniquement pour pouvoir
 * être rejetés explicitement (422) plutôt qu'ignorés silencieusement.
 */
public record ContributionUpdateRequest(
    Long donorId,
    Boolean anonymous,
    @Size(max = 500) String notes,
    @Size(max = 500) String remark,
    Long activityId,
    BigDecimal amount,
    Long projectId,
    Long categoryId,
    Long paymentChannelId,
    YearMonth periodMonth,
    LocalDate transactionDate
) {
    public boolean touchesFinancialFields() {
        return amount != null || projectId != null || categoryId != null
            || paymentChannelId != null || periodMonth != null || transactionDate != null;
    }
}
