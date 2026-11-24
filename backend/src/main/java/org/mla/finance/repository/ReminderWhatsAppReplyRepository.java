package org.mla.finance.repository;

import org.mla.finance.domain.ReminderWhatsAppReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReminderWhatsAppReplyRepository extends JpaRepository<ReminderWhatsAppReply, Long> {

    Optional<ReminderWhatsAppReply> findFirstByProviderMessageId(String providerMessageId);
}
