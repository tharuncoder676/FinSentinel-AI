package com.finsentinel.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinancialProfileRepository extends JpaRepository<FinancialProfileEntity, UUID> {
}
