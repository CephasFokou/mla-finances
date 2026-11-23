package org.mla.finance.repository;

import org.mla.finance.domain.ReminderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReminderDeliveryRepository extends JpaRepository<ReminderDelivery, Long> {

    List<ReminderDelivery> findByReminderIdInOrderByIdAsc(Collection<Long> reminderIds);

    /** Envoi WhatsApp correspondant à un wamid (statuts de livraison, réponse à un message précis). */
    java.util.Optional<ReminderDelivery> findFirstByProviderMessageId(String providerMessageId);

    /** Dernier envoi WhatsApp de MLA Finance vers un numéro (rattachement d'un texte libre sans contexte). */
    java.util.Optional<ReminderDelivery> findFirstByChannelAndRecipientOrderByCreatedAtDesc(
        org.mla.finance.domain.ReminderChannel channel, String recipient);
}
