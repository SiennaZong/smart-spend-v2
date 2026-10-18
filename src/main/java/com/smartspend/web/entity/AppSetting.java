package com.smartspend.web.entity;

import javax.persistence.*;

/**
 * 应用设置（键值对），按用户隔离存储 AI 接口配置
 */
@Entity
@Table(name = "app_settings", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "setting_key"}))
public class AppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 归属用户 ID；历史迁移数据为 null，由首个注册用户认领 */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "setting_key", nullable = false, length = 50)
    private String settingKey;

    @Column(name = "setting_value", length = 500)
    private String settingValue;

    public AppSetting() {
    }

    public AppSetting(Long userId, String settingKey, String settingValue) {
        this.userId = userId;
        this.settingKey = settingKey;
        this.settingValue = settingValue;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }
}
