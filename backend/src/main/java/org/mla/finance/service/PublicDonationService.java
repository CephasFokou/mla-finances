package org.mla.finance.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.DonationCategory;
import org.mla.finance.domain.Donor;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.PaymentChannel;
import org.mla.finance.domain.PaymentTransaction;
import org.mla.finance.domain.PaymentTransactionKind;
import org.mla.finance.domain.PaymentTransactionStatus;
import org.mla.finance.domain.Project;
import org.mla.finance.domain.ProjectStatus;
import org.mla.finance.exception.BusinessRuleViolationException;
import org.mla.finance.exception.InvalidRequestException;
import org.mla.finance.payment.PaymentInitiationResult;
import org.mla.finance.payment.PaymentProvider;
import org.mla.finance.payment.PaymentProviderResolver;
import org.mla.finance.payment.pawapay.PawaPayClient;
import org.mla.finance.payment.pawapay.PawaPayProperties;
import org.mla.finance.repository.ContributionRepository;
import org.mla.finance.repository.DonationCategoryRepository;
import org.mla.finance.repository.PaymentChannelRepository;
import org.mla.finance.repository.PaymentTransactionRepository;
import org.mla.finance.repository.ProjectRepository;
import org.mla.finance.reminder.ReminderPublicService;
import org.mla.finance.web.dto.PublicDonationRequest;
import org.mla.finance.web.dto.PublicDonationResponse;
import org.mla.finance.web.dto.PublicDonationStatusResponse;
import org.mla.finance.web.dto.PublicPaymentChannelResponse;
import org.mla.finance.web.dto.PublicProjectResponse;
import org.mla.finance.web.dto.PublicStatsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Point d'entrée du front-office public (page de don) : lecture des projets actifs à soutenir et
 * création d'une intention de don (PaymentTransaction), jamais d'une Contribution directement —
 * voir la javadoc de PaymentTransaction pour pourquoi. Ne nécessite aucune authentification
 * (SecurityConfig exempte /api/v1/public/**). Les codes DON-xx utilisés ici sont des codes
 * d'erreur internes à ce module, pas des règles de gestion du Cahier des Charges (contrairement à
 * RG-15, repris tel quel car c'est bien la même règle que celle déjà appliquée côté back-office).
 * <p>
 * Garde-fous d'un don payé en ligne (PawaPay), tous appliqués AVANT d'appeler le prestataire :
 * numéro Mobile Money camerounais valide et normalisé (DON-04), montant entier dans les bornes
 * configurées (DON-05, le XAF n'a pas de décimales), et nombre de demandes récentes vers un même
 * numéro plafonné (DON-06) — un attaquant ne doit pas pouvoir inonder un téléphone de demandes de
 * validation de paiement, ni nous faire dépenser des appels API. La limitation de débit par IP
 * (RateLimitFilter) s'ajoute à cela en amont.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicDonationService {

    private static final Logger log = LoggerFactory.getLogger(PublicDonationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    /** MSISDN camerounais : indicatif 237 + mobile à 9 chiffres commençant par 6. */
    private static final Pattern CAMEROON_MSISDN = Pattern.compile("^2376\\d{8}$");
    private static final Duration PHONE_THROTTLE_WINDOW = Duration.ofMinutes(10);

    private final ProjectRepository projectRepository;
    private final ReminderPublicService reminderPublicService;
    private final PaymentChannelRepository paymentChannelRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ContributionRepository contributionRepository;
    private final PaymentProviderResolver paymentProviderResolver;
    private final PawaPayProperties pawaPayProperties;
    private final DonationCategoryRepository categoryRepository;
    private final MonthlyContributionService monthlyContributionService;

    public List<PublicProjectResponse> activeProjects() {
        return projectRepository.findByStatus(ProjectStatus.ACTIVE).stream()
            .map(this::toPublicResponse)
            .toList();
    }

    /** Canaux proposés au public : actifs, sauf « Autre » (transferts internes), avec leur mode de paiement. */
    public List<PublicPaymentChannelResponse> paymentChannels() {
        return paymentChannelRepository.findByActiveTrueOrderBySortOrderAsc().stream()
            .filter(c -> !LedgerLookup.OTHER_CHANNEL_CODE.equals(c.getCode()))
            .map(c -> new PublicPaymentChannelResponse(c.getId(), c.getCode(), c.getLabel(), paymentProviderResolver.isOnline(c)))
            .toList();
    }

    /**
     * Suivi d'un don par son jeton opaque : statut, motif d'échec, et le contenu du reçu (montant,
     * objet, mois, nom, canal, numéro masqué, dates). Rien d'interne.
     */
    public PublicDonationStatusResponse status(String trackingToken) {
        PaymentTransaction tx = paymentTransactionRepository.findByTrackingToken(trackingToken)
            .orElseThrow(() -> new EntityNotFoundException("Don introuvable."));
        boolean monthly = tx.getKind() == PaymentTransactionKind.MONTHLY_CONTRIBUTION;
        String purpose = monthly
            ? (tx.getCategory() != null ? tx.getCategory().getName() : "Caisse du MLA")
            : (tx.getProject() != null ? tx.getProject().getName() : null);
        List<String> months = tx.getCoveredMonths().stream()
            .sorted()
            .map(m -> m.toString().substring(0, 7))
            .toList();
        return new PublicDonationStatusResponse(tx.getReference(), tx.getStatus().name(), tx.getFailureCode(),
            tx.getStatus() == PaymentTransactionStatus.FAILED ? publicFailureMessage(tx.getFailureCode()) : null,
            tx.getKind().name(),
            tx.getAmount(),
            purpose,
            months,
            tx.isAnonymous() ? null : tx.getDonorName(),
            tx.getPaymentChannel() != null ? tx.getPaymentChannel().getLabel() : null,
            maskForReceipt(tx.getPayerPhone()),
            tx.getCreatedAt(),
            tx.getConfirmedAt());
    }

    /** 237677123456 → « 6** ** 34 56 » (assez pour se reconnaître, pas pour être réutilisé). */
    static String maskForReceipt(String msisdn) {
        if (msisdn == null || msisdn.length() < 9) {
            return null;
        }
        String national = msisdn.substring(msisdn.length() - 9);
        return national.charAt(0) + "** ** " + national.substring(5, 7) + " " + national.substring(7);
    }

    public PublicStatsResponse stats() {
        long activeProjectCount = projectRepository.findByStatus(ProjectStatus.ACTIVE).size();
        // Seules les sommes réellement collectées pour des collectes dédiées : ni la caisse interne
        // (contributions mensuelles), ni les mouvements techniques (transferts, avances, ajustements).
        return new PublicStatsResponse(
            contributionRepository.sumCollectedForProjects(MovementNature.COLLECTED),
            contributionRepository.countCollectedForProjects(MovementNature.COLLECTED),
            activeProjectCount);
    }

    /**
     * Crée l'intention de paiement d'un don à une collecte ou d'une contribution mensuelle, puis
     * l'envoie au prestataire. Mêmes garde-fous pour les deux : canal actif (RG-15), et en ligne
     * numéro payeur valide (DON-04), montant total dans les bornes (DON-05) et anti-abus par
     * numéro (DON-06).
     */
    @Transactional
    public PublicDonationResponse submit(PublicDonationRequest request) {
        PaymentTransaction transaction = request.kind() == PaymentTransactionKind.MONTHLY_CONTRIBUTION
            ? monthlyContributionIntent(request)
            : donationIntent(request);
        PaymentChannel channel = paymentChannelRepository.findById(request.paymentChannelId())
            .orElseThrow(() -> new EntityNotFoundException("Canal de paiement introuvable : " + request.paymentChannelId()));
        if (!channel.isActive()) {
            throw new BusinessRuleViolationException("RG-15", "Le canal de paiement '" + channel.getLabel() + "' est désactivé.");
        }
        PaymentProvider provider = paymentProviderResolver.resolve(channel);
        boolean online = paymentProviderResolver.isOnline(channel);

        transaction.setPaymentChannel(channel);
        transaction.setReference(generateReference());
        transaction.setTrackingToken(UUID.randomUUID().toString());
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        transaction.setProvider(provider.code());

        if (online) {
            String msisdn = normalizeMsisdn(request.payerPhone());
            validateOnlineAmount(transaction.getAmount());
            throttlePhone(msisdn);
            transaction.setPayerPhone(msisdn);
            // Clé d'idempotence RG-20 : générée et PERSISTÉE avant l'appel au prestataire, pour que
            // toute relecture (callback, polling) retrouve la transaction même si la réponse à
            // l'initiation se perd — et pour qu'un rejeu de la requête ne crée jamais deux dépôts.
            transaction.setDepositId(UUID.randomUUID().toString());
            transaction.setStatus(PaymentTransactionStatus.PROCESSING);
            transaction.setNextCheckAt(Instant.now().plusSeconds(15));
        }
        PaymentTransaction saved = paymentTransactionRepository.saveAndFlush(transaction);

        PaymentInitiationResult result;
        try {
            result = provider.initiate(saved);
        } catch (RuntimeException e) {
            // Jamais de rollback ici : la demande a peut-être atteint le prestataire. On garde la
            // ligne (depositId persisté) et la réconciliation tranchera.
            log.error("Initiation du paiement {} : erreur inattendue du prestataire {}", saved.getReference(), provider.code(), e);
            result = online ? PaymentInitiationResult.processing(saved.getDepositId(), "UNKNOWN") : PaymentInitiationResult.manual();
        }
        saved.setStatus(result.status());
        saved.setProviderStatus(result.providerStatus());
        if (result.providerReference() != null) {
            saved.setProviderReference(result.providerReference());
        }
        if (result.status() == PaymentTransactionStatus.FAILED) {
            saved.setFailureCode(result.failureCode());
            saved.setFailureMessage(result.failureMessage());
            saved.setNextCheckAt(null);
            log.info("Don {} refusé à l'initiation par {} : {}", saved.getReference(), provider.code(), result.failureCode());
        } else if (result.status() == PaymentTransactionStatus.PENDING) {
            saved.setNextCheckAt(null);
        }
        saved = paymentTransactionRepository.save(saved);

        return new PublicDonationResponse(saved.getReference(), saved.getStatus().name(), online ? "ONLINE" : "MANUAL",
            saved.getTrackingToken(),
            saved.getStatus() == PaymentTransactionStatus.FAILED ? publicFailureMessage(saved.getFailureCode()) : null);
    }

    /** Don à une collecte ouverte : projectId et amount requis (400), collecte active (DON-01), nom sauf anonymat (DON-02). */
    private PaymentTransaction donationIntent(PublicDonationRequest request) {
        if (request.projectId() == null) {
            throw new InvalidRequestException("projectId", "Choisissez la collecte à soutenir.");
        }
        if (request.amount() == null) {
            throw new InvalidRequestException("amount", "Indiquez le montant du don.");
        }
        Project project = projectRepository.findById(request.projectId())
            .orElseThrow(() -> new EntityNotFoundException("Projet introuvable : " + request.projectId()));
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessRuleViolationException("DON-01", "Ce projet n'accepte plus de dons pour le moment.");
        }
        if (!request.anonymous() && (request.donorName() == null || request.donorName().isBlank())) {
            throw new BusinessRuleViolationException("DON-02", "Le nom du donateur est requis, sauf pour un don anonyme.");
        }
        // Lien signé d'une relance de collecte : le don est rattaché au membre en interne (le site public reste anonyme),
        // ce qui clôt sa relance à la confirmation du paiement.
        Donor member = reminderPublicService.memberForProjectDonation(request.reminderToken(), project.getId()).orElse(null);
        return PaymentTransaction.builder()
            .kind(PaymentTransactionKind.DONATION)
            .project(project)
            .donor(member)
            .amount(request.amount())
            .anonymous(request.anonymous())
            .donorName(request.anonymous() ? null : request.donorName())
            .donorContact(request.anonymous() ? null : request.donorContact())
            .build();
    }

    /**
     * Contribution mensuelle d'un membre à la caisse MLA : donorId, mois et montant par mois requis
     * (400) ; parcours activé (DON-09), membre actif (DON-10), mois dans la plage proposée (DON-11),
     * montant entier (DON-05). Jamais anonyme : le membre est connu, c'est lui qui alimente le
     * calendrier des contributions.
     */
    private PaymentTransaction monthlyContributionIntent(PublicDonationRequest request) {
        if (request.donorId() == null) {
            throw new InvalidRequestException("donorId", "Choisissez votre nom dans la liste des membres.");
        }
        if (request.months() == null || request.months().isEmpty()) {
            throw new InvalidRequestException("months", "Choisissez au moins un mois.");
        }
        if (request.amountPerMonth() == null) {
            throw new InvalidRequestException("amountPerMonth", "Indiquez le montant par mois.");
        }
        if (!monthlyContributionService.settings().isEnabled()) {
            throw new BusinessRuleViolationException("DON-09", "La contribution mensuelle en ligne est momentanément désactivée.");
        }
        Donor member = monthlyContributionService.payingMember(request.donorId());
        List<LocalDate> months = monthlyContributionService.requestedMonths(request.months());
        if (request.amountPerMonth().stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleViolationException("DON-05", "Le montant par mois doit être un montant entier en FCFA.");
        }
        DonationCategory caisse = categoryRepository.findFirstByMainFundTrue()
            .orElseThrow(() -> new BusinessRuleViolationException("DON-09", "La caisse du MLA n'est pas configurée."));
        PaymentTransaction transaction = PaymentTransaction.builder()
            .kind(PaymentTransactionKind.MONTHLY_CONTRIBUTION)
            .category(caisse)
            .donor(member)
            .donorName(member.getLabel())
            .anonymous(false)
            .amountPerMonth(request.amountPerMonth())
            .amount(request.amountPerMonth().multiply(BigDecimal.valueOf(months.size())))
            .build();
        transaction.getCoveredMonths().addAll(months);
        return transaction;
    }

    /** Accepte 6XXXXXXXX, 06..., +237 6..., 237 6... (espaces, points, tirets ignorés) ; renvoie 2376XXXXXXXX. */
    static String normalizeMsisdn(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleViolationException("DON-04", "Indiquez le numéro Mobile Money à débiter.");
        }
        String digits = raw.replaceAll("[\\s().-]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("00237")) {
            digits = digits.substring(2);
        }
        if (!digits.startsWith("237")) {
            if (digits.startsWith("0") && digits.length() == 10) {
                digits = digits.substring(1);
            }
            digits = "237" + digits;
        }
        if (!CAMEROON_MSISDN.matcher(digits).matches()) {
            throw new BusinessRuleViolationException("DON-04",
                "Numéro Mobile Money invalide : attendu un numéro camerounais à 9 chiffres commençant par 6 (ex. 6xx xx xx xx).");
        }
        return digits;
    }

    private void validateOnlineAmount(BigDecimal amount) {
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleViolationException("DON-05", "Un don Mobile Money doit être un montant entier en FCFA.");
        }
        if (amount.compareTo(pawaPayProperties.getMinAmount()) < 0 || amount.compareTo(pawaPayProperties.getMaxAmount()) > 0) {
            throw new BusinessRuleViolationException("DON-05", "Un don Mobile Money doit être compris entre "
                + pawaPayProperties.getMinAmount().toPlainString() + " et " + pawaPayProperties.getMaxAmount().toPlainString() + " FCFA.");
        }
    }

    private void throttlePhone(String msisdn) {
        long recent = paymentTransactionRepository.countByPayerPhoneAndCreatedAtAfter(msisdn, Instant.now().minus(PHONE_THROTTLE_WINDOW));
        if (recent >= pawaPayProperties.getMaxInitiationsPerPhone()) {
            log.warn("Anti-abus : {} demandes de paiement en 10 min vers {}", recent, PawaPayClient.maskPhone(msisdn));
            throw new BusinessRuleViolationException("DON-06",
                "Trop de demandes de paiement récentes pour ce numéro. Patientez quelques minutes avant de réessayer.");
        }
    }

    /** Message montré au donateur selon le code d'échec PawaPay (jamais le message brut du prestataire). */
    static String publicFailureMessage(String failureCode) {
        if (failureCode == null) {
            return "Le paiement n'a pas abouti. Vous pouvez réessayer.";
        }
        return switch (failureCode) {
            case "PAYMENT_NOT_APPROVED" -> "Le paiement n'a pas été validé sur votre téléphone. Vous pouvez réessayer.";
            case "INSUFFICIENT_BALANCE" -> "Solde insuffisant sur le compte Mobile Money.";
            case "PAYER_LIMIT_REACHED" -> "Plafond de votre compte Mobile Money atteint pour ce paiement.";
            case "PAYER_NOT_FOUND" -> "Ce numéro n'a pas de compte Mobile Money actif chez cet opérateur.";
            case "PAYMENT_IN_PROGRESS" -> "Une autre demande de paiement est déjà en cours sur ce numéro. Terminez-la puis réessayez.";
            case "DEPOSIT_NOT_FOUND" -> "La demande de paiement n'a pas pu être transmise. Vous pouvez réessayer.";
            case "PROVIDER_TEMPORARILY_UNAVAILABLE" -> "L'opérateur est momentanément indisponible. Réessayez dans quelques minutes.";
            default -> "Le paiement n'a pas abouti. Vous pouvez réessayer ou choisir un autre moyen.";
        };
    }

    private PublicProjectResponse toPublicResponse(Project project) {
        BigDecimal collected = project.getOpeningBalance().add(contributionRepository.sumAmountByProjectId(project.getId()));
        Integer progress = null;
        if (project.getTargetAmount() != null && project.getTargetAmount().signum() > 0) {
            progress = collected.multiply(BigDecimal.valueOf(100))
                .divide(project.getTargetAmount(), 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100))
                .max(BigDecimal.ZERO)
                .intValue();
        }
        return new PublicProjectResponse(project.getId(), project.getName(), project.getDescription(),
            project.getTargetAmount(), collected, progress);
    }

    private static String generateReference() {
        return "MLA-" + (100000 + RANDOM.nextInt(900000));
    }
}
