package org.mla.finance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class MlaFinanceApplicationTests {

    @Test
    void contextLoads() {
        // Vérifie que le contexte Spring (JPA + Liquibase + sécurité) démarre correctement.
    }
}
