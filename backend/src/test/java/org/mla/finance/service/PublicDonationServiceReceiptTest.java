package org.mla.finance.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Numéro masqué du reçu de don. */
class PublicDonationServiceReceiptTest {

    @Test
    void receiptPhoneShowsOnlyTheLastFourDigits() {
        assertThat(PublicDonationService.maskForReceipt("237677123456")).isEqualTo("6** ** 34 56");
        assertThat(PublicDonationService.maskForReceipt(null)).isNull();
    }
}
