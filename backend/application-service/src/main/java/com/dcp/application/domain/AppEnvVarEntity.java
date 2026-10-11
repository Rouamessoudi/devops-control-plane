package com.dcp.application.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_env_vars")
public class AppEnvVarEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @Column(name = "application_id", nullable = false, length = 36)
    private UUID applicationId;

    @Column(name = "env_key", nullable = false, length = 128)
    private String envKey;

    @Column(name = "value_ciphertext", nullable = false, length = 2000)
    private String valueCiphertext;

    @Column(name = "secret_flag", nullable = false)
    private boolean secretFlag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getEnvKey() {
        return envKey;
    }

    public void setEnvKey(String envKey) {
        this.envKey = envKey;
    }

    public String getValueCiphertext() {
        return valueCiphertext;
    }

    public void setValueCiphertext(String valueCiphertext) {
        this.valueCiphertext = valueCiphertext;
    }

    public boolean isSecretFlag() {
        return secretFlag;
    }

    public void setSecretFlag(boolean secretFlag) {
        this.secretFlag = secretFlag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
