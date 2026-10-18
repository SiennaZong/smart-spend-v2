package com.smartspend.web.repo;

import com.smartspend.web.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

    Optional<AppSetting> findByUserIdAndSettingKey(Long userId, String settingKey);

    List<AppSetting> findByUserIdIsNull();
}
