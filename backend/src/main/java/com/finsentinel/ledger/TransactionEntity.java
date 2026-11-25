package com.finsentinel.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class TransactionEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "transfer_account_id")
    private UUID transferAccountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "tx_type", nullable = false, length = 24)
    private String txType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(length = 160)
    private String merchant;

    @Column(length = 500)
    private String description;

    @Column(name = "is_essential", nullable = false)
    private boolean essential;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring;

    @Column(name = "recurrence_rule", length = 32)
    private String recurrenceRule;

    @Column(name = "dedup_hash", length = 64)
    private String dedupHash;

    @Column(name = "anomaly_status", nullable = false, length = 24)
    private String anomalyStatus = "NONE";

    @Column(name = "suggested_category_id")
    private UUID suggestedCategoryId;

    @Column(name = "linked_payment_record_id")
    private UUID linkedPaymentRecordId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public UUID getTransferAccountId() { return transferAccountId; }
    public void setTransferAccountId(UUID transferAccountId) { this.transferAccountId = transferAccountId; }
    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public String getTxType() { return txType; }
    public void setTxType(String txType) { this.txType = txType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public void setOccurredOn(LocalDate occurredOn) { this.occurredOn = occurredOn; }
    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEssential() { return essential; }
    public void setEssential(boolean essential) { this.essential = essential; }
    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public String getRecurrenceRule() { return recurrenceRule; }
    public void setRecurrenceRule(String recurrenceRule) { this.recurrenceRule = recurrenceRule; }
    public String getDedupHash() { return dedupHash; }
    public void setDedupHash(String dedupHash) { this.dedupHash = dedupHash; }
    public String getAnomalyStatus() { return anomalyStatus; }
    public void setAnomalyStatus(String anomalyStatus) { this.anomalyStatus = anomalyStatus; }
    public UUID getSuggestedCategoryId() { return suggestedCategoryId; }
    public void setSuggestedCategoryId(UUID suggestedCategoryId) { this.suggestedCategoryId = suggestedCategoryId; }
    public UUID getLinkedPaymentRecordId() { return linkedPaymentRecordId; }
    public void setLinkedPaymentRecordId(UUID linkedPaymentRecordId) { this.linkedPaymentRecordId = linkedPaymentRecordId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
