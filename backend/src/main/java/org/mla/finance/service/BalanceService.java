package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.repository.WithdrawalRepository;
import org.mla.finance.web.dto.CategoryBalanceResponse;
import org.mla.finance.web.dto.ProjectBalanceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/** RG-07 : le solde n'est jamais stocké, il est recalculé à chaque lecture. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceService {

    private static final Set<WithdrawalStatus> ENGAGED_STATUSES = EnumSet.of(WithdrawalStatus.PENDING, WithdrawalStatus.APPROVED);
    private static final Set<WithdrawalStatus> PROCESSED_STATUS = EnumSet.of(WithdrawalStatus.PROCESSED);

    private final ContributionRepository contributionRepository;
    private final ProjectRepository projectRepository;
    private final DonationCategoryRepository categoryRepository;
    private final WithdrawalRepository withdrawalRepository;

    public ProjectBalanceResponse projectBalance(Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + projectId));
        BigDecimal totalContributions = contributionRepository.sumAmountByProjectId(projectId);
        BigDecimal totalWithdrawals = withdrawalRepository.sumAmountByProjectIdAndStatusIn(projectId, PROCESSED_STATUS);
        BigDecimal engagedAmount = withdrawalRepository.sumAmountByProjectIdAndStatusIn(projectId, ENGAGED_STATUSES);
        BigDecimal currentBalance = project.getOpeningBalance().add(totalContributions).subtract(totalWithdrawals);
        BigDecimal provisionalBalance = currentBalance.subtract(engagedAmount);

        return new ProjectBalanceResponse(
            projectId,
            project.getOpeningBalance(),
            totalContributions,
            totalWithdrawals,
            currentBalance,
            engagedAmount,
            provisionalBalance,
            provisionalBalance.signum() < 0,
            contributionRepository.countByProjectId(projectId),
            withdrawalRepository.countByProjectIdAndStatus(projectId, WithdrawalStatus.PENDING));
    }

    public CategoryBalanceResponse categoryBalance(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new EntityNotFoundException("Catégorie introuvable : " + categoryId);
        }
        BigDecimal totalContributions = contributionRepository.sumAmountByCategoryId(categoryId);
        BigDecimal totalWithdrawals = withdrawalRepository.sumAmountByCategoryIdAndStatusIn(categoryId, PROCESSED_STATUS);
        BigDecimal engagedAmount = withdrawalRepository.sumAmountByCategoryIdAndStatusIn(categoryId, ENGAGED_STATUSES);
        BigDecimal currentBalance = totalContributions.subtract(totalWithdrawals);
        BigDecimal provisionalBalance = currentBalance.subtract(engagedAmount);

        return new CategoryBalanceResponse(
            categoryId,
            BigDecimal.ZERO,
            totalContributions,
            totalWithdrawals,
            currentBalance,
            engagedAmount,
            provisionalBalance,
            provisionalBalance.signum() < 0,
            contributionRepository.countByCategoryId(categoryId),
            withdrawalRepository.countByCategoryIdAndStatus(categoryId, WithdrawalStatus.PENDING));
    }
}
