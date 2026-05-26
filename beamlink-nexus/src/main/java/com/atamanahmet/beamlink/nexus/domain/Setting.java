package com.atamanahmet.beamlink.nexus.domain;

import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Runtime configuration persisted to DB.
 * Takes precedence over .env defaults after first boot.
 */
@Entity
@Table(name = "settings")
@Getter
@NoArgsConstructor
public class Setting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private SettingKey key;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private Instant updatedAt;

    public Setting(SettingKey key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public void update(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}