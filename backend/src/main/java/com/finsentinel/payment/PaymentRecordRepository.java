package com.finsentinel.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecordEntity, UUID> {
    List<PaymentRecordEntity> findByUserIdOrderByPaidOnDescCreatedAtDesc(UUID userId);
    List<PaymentRecordEntity> findByObligationIdAndUserIdOrderByPaidOnDesc(UUID obligationId, UUID userId);
}
