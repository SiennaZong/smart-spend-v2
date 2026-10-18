package com.smartspend.web.entity;

import javax.persistence.*;
import java.io.Serializable;

/**
 * 分类月度预算实体（按月份 + 分类唯一）
 */
@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "budget_month", "category"}))
public class Budget implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 月份，格式 yyyy-MM（列名避开 month 保留字） */
    @Column(name = "budget_month", nullable = false, length = 7)
    private String month;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false)
    private double monthlyLimit;

    /** 归属用户 ID；历史迁移数据为 null，由首个注册用户认领 */
    @Column(name = "user_id")
    private Long userId;

    public Budget() {
    }

    public Budget(String month, String category, double monthlyLimit) {
        this.month = month;
        this.category = category;
        this.monthlyLimit = Math.round(monthlyLimit * 100.0) / 100.0;
    }

    public Long getId() {
        return id;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(double monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
