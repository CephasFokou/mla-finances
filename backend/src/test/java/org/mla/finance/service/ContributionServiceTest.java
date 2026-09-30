package org.mla.finance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.MovementType;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.Project;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.ConflictException;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.web.dto.CancelRequest;
import org.mla.finance.web.dto.ContributionRequest;
import org.mla.finance.web.dto.ContributionResponse;
import org.mla.finance.web.dto.ContributionUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ContributionServiceTest {

    @Autowired ContributionService contributionService;
    @Autowired BalanceService balanceService;
    @Autowired ProjectRepository projectRepository;
    @Autowired DonationCategoryRepository categoryRepository;
    @Autowired PaymentChannelRepository paymentChannelRepository;

    Project project;
    DonationCategory category;
    PaymentChannel channel;

    @BeforeEach
    void setUp() {
        project = projectRepository.save(Project.builder().name("Toiture").openingBalance(new BigDecimal("43000.00")).build());
        category = categoryRepository.save(DonationCategory.builder().name("Caisse générale (test)").build());
        channel = paymentChannelRepository.findByActiveTrueOrderBySortOrderAsc().get(0);
    }

    private ContributionRequest request(Long projectId, Long categoryId, String amount) {
        return new ContributionRequest(projectId, categoryId, null, channel.getId(), new BigDecimal(amount),
            YearMonth.of(2026, 9), LocalDate.of(2026, 9, 18), false, "Don du dimanche");
    }

    @Test
    void rejectsContributionWithNeitherProjectNorCategory() {
        assertThatThrownBy(() -> contributionService.create(request(null, null, "100.00")))
            .isInstanceOf(BusinessRuleViolationException.class)
            .extracting("rule").isEqualTo("RG-02");
    }

    @Test
    void rejectsContributionWithBothProjectAndCategory() {
        assertThatThrownBy(() -> contributionService.create(request(project.getId(), category.getId(), "100.00")))
            .isInstanceOf(BusinessRuleViolationException.class)
            .extracting("rule").isEqualTo("RG-02");
    }

    @Test
    void createNormalizesPeriodMonthToFirstDayAndIsStandard() {
        ContributionResponse created = contributionService.create(request(project.getId(), null, "25000.00"));

        assertThat(created.movementType()).isEqualTo(MovementType.STANDARD);
        assertThat(created.periodMonth()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(balanceService.projectBalance(project.getId()).currentBalance()).isEqualByComparingTo("68000.00");
    }

    @Test
    void cancelCreatesOppositeAdjustmentAndRestoresBalance() {
        BigDecimal before = balanceService.projectBalance(project.getId()).currentBalance();
        ContributionResponse original = contributionService.create(request(project.getId(), null, "25000.00"));

        ContributionResponse adjustment = contributionService.cancel(original.id(), new CancelRequest("Montant dupliqué"));

        assertThat(adjustment.movementType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(adjustment.amount()).isEqualByComparingTo("-25000.00");
        assertThat(adjustment.adjustsContributionId()).isEqualTo(original.id());
        assertThat(adjustment.project().id()).isEqualTo(project.getId());
        assertThat(adjustment.category()).isNull();
        assertThat(balanceService.projectBalance(project.getId()).currentBalance()).isEqualByComparingTo(before);
        assertThat(contributionService.get(original.id()).amount()).isEqualByComparingTo("25000.00");
    }

    @Test
    void cancellingTwiceIsRejectedAsConflict() {
        ContributionResponse original = contributionService.create(request(project.getId(), null, "500.00"));
        contributionService.cancel(original.id(), new CancelRequest("Erreur"));

        assertThatThrownBy(() -> contributionService.cancel(original.id(), new CancelRequest("Encore")))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void adjustmentCannotBeCancelled() {
        ContributionResponse original = contributionService.create(request(null, category.getId(), "500.00"));
        ContributionResponse adjustment = contributionService.cancel(original.id(), new CancelRequest("Erreur"));

        assertThatThrownBy(() -> contributionService.cancel(adjustment.id(), new CancelRequest("Annuler l'annulation")))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void updateRejectsFinancialFieldsAndChangesOnlyNonFinancialOnes() {
        ContributionResponse original = contributionService.create(request(project.getId(), null, "500.00"));

        assertThatThrownBy(() -> contributionService.update(original.id(),
            new ContributionUpdateRequest(null, false, "x", new BigDecimal("1.00"), null, null, null, null, null)))
            .isInstanceOf(BusinessRuleViolationException.class);

        ContributionResponse updated = contributionService.update(original.id(),
            new ContributionUpdateRequest(null, true, "Note corrigée", null, null, null, null, null, null));
        assertThat(updated.anonymous()).isTrue();
        assertThat(updated.notes()).isEqualTo("Note corrigée");
        assertThat(updated.amount()).isEqualByComparingTo("500.00");
    }
}
