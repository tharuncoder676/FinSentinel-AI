package com.finsentinel.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentScheduleRepository extends JpaRepository<PaymentScheduleEntity, UUID> {
    List<PaymentScheduleEntity> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);
}
