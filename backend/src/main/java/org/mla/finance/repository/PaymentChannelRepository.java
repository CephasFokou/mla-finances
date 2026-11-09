package org.mla.finance.repository;

import org.mla.finance.domain.PaymentChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentChannelRepository extends JpaRepository<PaymentChannel, Long> {
    List<PaymentChannel> findByActiveTrueOrderBySortOrderAsc();

    List<PaymentChannel> findAllByOrderBySortOrderAsc();

    java.util.Optional<PaymentChannel> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    @org.springframework.data.jpa.repository.Query("select coalesce(max(c.sortOrder), 0) from PaymentChannel c")
    int maxSortOrder();
}
