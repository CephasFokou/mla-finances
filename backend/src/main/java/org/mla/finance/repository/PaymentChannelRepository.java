package org.mla.finance.repository;

import org.mla.finance.domain.PaymentChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentChannelRepository extends JpaRepository<PaymentChannel, Long> {
    List<PaymentChannel> findByActiveTrueOrderBySortOrderAsc();
}
