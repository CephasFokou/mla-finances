package org.mla.finance.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.mla.finance.domain.MovementNature;
import org.mla.finance.domain.WithdrawalStatus;
import org.mla.finance.service.WithdrawalCsvExporter;
import org.mla.finance.service.WithdrawalFilter;
import org.mla.finance.service.WithdrawalPayoutService;
import org.mla.finance.service.WithdrawalService;
import org.mla.finance.service.WithdrawalVoucherService;
import org.mla.finance.web.dto.MobileMoneyBalanceResponse;
import org.mla.finance.web.dto.PayoutOptionsResponse;
import org.mla.finance.web.dto.PayoutRequest;
import org.mla.finance.web.dto.WithdrawableProjectResponse;
import org.mla.finance.web.dto.WithdrawalCreateRequest;
import org.mla.finance.web.dto.WithdrawalRejectRequest;
import org.mla.finance.web.dto.WithdrawalResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final WithdrawalService withdrawalService;
    private final WithdrawalPayoutService payoutService;
    private final WithdrawalCsvExporter csvExporter;
    private final WithdrawalVoucherService voucherService;

    @GetMapping
    public Page<WithdrawalResponse> list(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) WithdrawalStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) MovementNature nature,
        @RequestParam(required = false) Long activityId,
        @RequestParam(required = false) Long personId,
        @RequestParam(required = false) Boolean selfApproved,
        @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return withdrawalService.search(
            new WithdrawalFilter(projectId, categoryId, status, dateFrom, dateTo, nature, activityId, personId, selfApproved), pageable);
    }

    /** Collectes du formulaire de sortie : ouvertes, puis clôturées avec un solde restant (et ce solde). */
    @GetMapping("/projects")
    public List<WithdrawableProjectResponse> withdrawableProjects() {
        return withdrawalService.withdrawableProjects();
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) WithdrawalStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) MovementNature nature,
        @RequestParam(required = false) Long activityId,
        @RequestParam(required = false) Long personId,
        @RequestParam(required = false) Boolean selfApproved
    ) {
        String csv = csvExporter.export(
            new WithdrawalFilter(projectId, categoryId, status, dateFrom, dateTo, nature, activityId, personId, selfApproved));
        return ResponseEntity.ok()
            .contentType(TEXT_CSV_UTF8)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("retraits.csv").build().toString())
            .body(csv);
    }

    /** Décaissement Mobile Money activé ? Numéros MLA proposés (liste fermée, masqués). */
    @GetMapping("/payout-options")
    public PayoutOptionsResponse payoutOptions() {
        return payoutService.options();
    }

    /** Encart « Solde Mobile Money du MLA » de la page Retraits (lecture seule, wallet-ledger). */
    @GetMapping("/mobile-money-balance")
    public MobileMoneyBalanceResponse mobileMoneyBalance() {
        return payoutService.balance();
    }

    @GetMapping("/{id}")
    public WithdrawalResponse get(@PathVariable Long id) {
        return withdrawalService.get(id);
    }

    /**
     * Bon de décaissement PDF d'un retrait PROCESSED (409 sinon), produit à la demande. Même droit que
     * la lecture du retrait ; chaque téléchargement est tracé (WITHDRAWAL_VOUCHER_DOWNLOADED).
     */
    @GetMapping(value = "/{id}/voucher.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> voucher(@PathVariable Long id) {
        WithdrawalVoucherService.Voucher voucher = voucherService.generate(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(voucher.filename()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(voucher.pdf());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS')")
    public ResponseEntity<WithdrawalResponse> create(@Valid @RequestBody WithdrawalCreateRequest request) {
        WithdrawalResponse created = withdrawalService.create(request);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
            .body(created);
    }

    /** Sortie déjà effectuée (cahier de caisse) : enregistrée directement comme décaissée. */
    @PostMapping("/record")
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS') and hasAuthority('VALIDATE_WITHDRAWALS')")
    public ResponseEntity<WithdrawalResponse> record(@Valid @RequestBody WithdrawalCreateRequest request) {
        WithdrawalResponse created = withdrawalService.record(request);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/withdrawals/{id}").buildAndExpand(created.id()).toUri())
            .body(created);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('VALIDATE_WITHDRAWALS')")
    public WithdrawalResponse approve(@PathVariable Long id) {
        return withdrawalService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('VALIDATE_WITHDRAWALS')")
    public WithdrawalResponse reject(@PathVariable Long id, @Valid @RequestBody WithdrawalRejectRequest request) {
        return withdrawalService.reject(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS')")
    public WithdrawalResponse cancel(@PathVariable Long id) {
        return withdrawalService.cancel(id);
    }

    @PostMapping("/{id}/process")
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS')")
    public WithdrawalResponse process(@PathVariable Long id) {
        return withdrawalService.process(id);
    }

    /** Décaisser un retrait APPROVED par Mobile Money vers un numéro MLA approuvé (payout wallet-ledger). */
    @PostMapping("/{id}/payout")
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS')")
    public WithdrawalResponse payout(@PathVariable Long id, @Valid @RequestBody PayoutRequest request) {
        return payoutService.initiate(id, request.recipientKey());
    }

    /** Relecture immédiate du statut du payout (« Vérifier le statut ») : ne fait que ce que le polling ferait. */
    @PostMapping("/{id}/payout/recheck")
    @PreAuthorize("hasAuthority('MANAGE_WITHDRAWALS')")
    public WithdrawalResponse recheckPayout(@PathVariable Long id) {
        return payoutService.recheck(id);
    }
}
