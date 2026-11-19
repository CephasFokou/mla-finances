package org.mla.finance.payment;

import org.junit.jupiter.api.Test;
import org.mla.finance.payment.payout.MsisdnMask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mode manuel (PAWAPAY_ENABLED=false) : l'action n'est pas proposée et l'API la refuse. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.payment.pawapay.enabled=false")
class WithdrawalPayoutManualModeTest {

    @Autowired MockMvc mockMvc;

    @Test
    void payoutIsNotOfferedInManualMode() throws Exception {
        var manager = jwt().jwt(b -> b.subject("999")).authorities(new SimpleGrantedAuthority("MANAGE_WITHDRAWALS"));
        mockMvc.perform(get("/api/v1/withdrawals/payout-options").with(manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.recipients.length()").value(0));
        mockMvc.perform(get("/api/v1/withdrawals/mobile-money-balance").with(manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(post("/api/v1/withdrawals/1/payout").with(manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"recipientKey\":\"ORANGE_CMR-2882\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void masksCameroonianNumbers() {
        assertThat(MsisdnMask.mask("237697272882")).isEqualTo("6** ** 28 82");
        assertThat(MsisdnMask.mask("237651059561")).isEqualTo("6** ** 95 61");
        assertThat(MsisdnMask.mask(null)).isNull();
    }
}
