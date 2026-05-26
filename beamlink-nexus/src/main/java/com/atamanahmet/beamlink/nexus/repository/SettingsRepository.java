package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.Setting;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettingsRepository extends JpaRepository<Setting, String> {

    Optional<Setting> findByKey(SettingKey key);
}