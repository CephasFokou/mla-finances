package org.mla.finance.voucher;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "0       | Zéro franc CFA",
        "1       | Un franc CFA",
        "100     | Cent francs CFA",
        "1000    | Mille francs CFA",
        "21000   | Vingt et un mille francs CFA",
        "80000   | Quatre-vingt mille francs CFA",
        "1250500 | Un million deux cent cinquante mille cinq cents francs CFA",
        "71      | Soixante et onze francs CFA",
        "80      | Quatre-vingts francs CFA",
        "81      | Quatre-vingt-un francs CFA",
        "91      | Quatre-vingt-onze francs CFA",
        "99      | Quatre-vingt-dix-neuf francs CFA",
        "200     | Deux cents francs CFA",
        "201     | Deux cent un francs CFA",
        "200000  | Deux cent mille francs CFA",
        "25000   | Vingt-cinq mille francs CFA",
        "2000000 | Deux millions de francs CFA",
        "80000000| Quatre-vingts millions de francs CFA",
        "1001001 | Un million mille un francs CFA",
    })
    void spellsAmountsInFrench(String amount, String expected) {
        assertThat(AmountInWords.fcfa(new BigDecimal(amount))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"100.00, Cent francs CFA", "25000.40, Vingt-cinq mille francs CFA"})
    void ignoresTheScaleOfStoredAmounts(String amount, String expected) {
        assertThat(AmountInWords.fcfa(new BigDecimal(amount))).isEqualTo(expected);
    }
}
