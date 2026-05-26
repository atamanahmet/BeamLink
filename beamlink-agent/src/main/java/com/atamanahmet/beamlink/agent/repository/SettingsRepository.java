package com.atamanahmet.beamlink.agent.repository;

import com.atamanahmet.beamlink.agent.domain.Setting;
import com.atamanahmet.beamlink.agent.domain.enums.SettingKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettingsRepository extends JpaRepository<Setting, String> {

    Optional<Setting> findByKey(SettingKey key);
}