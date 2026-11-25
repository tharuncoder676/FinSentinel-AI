package com.finsentinel.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByDedupKey(String dedupKey);
    long countByUserIdAndReadFalse(UUID userId);
    long countByDeliveryStatus(String deliveryStatus);
}
