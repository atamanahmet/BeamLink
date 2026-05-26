package com.atamanahmet.beamlink.agent.domain;

import com.atamanahmet.beamlink.agent.domain.enums.SettingKey;
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
@Setter
@NoArgsConstructor
public class Setting {

    @Id
    @Column(nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
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
}