package com.atamanahmet.beamlink.peer.repository;

import com.atamanahmet.beamlink.peer.domain.Setting;
import com.atamanahmet.beamlink.peer.domain.enums.SettingKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettingsRepository extends JpaRepository<Setting, String> {

    Optional<Setting> findByKey(SettingKey key);
}