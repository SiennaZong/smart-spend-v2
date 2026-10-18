package com.smartspend.web.entity;

import javax.persistence.*;
import java.time.LocalDate;

/**
 * 交易记录实体
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    /** INCOME / EXPENSE */
    @Column(nullable = false, length = 10)
    private String type;

    /** 分类名称，对应 Category 枚举名 */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false)
    private double amount;

    @Column(length = 500)
    private String note;

    /** 归属用户 ID；历史迁移数据为 null，由首个注册用户认领 */
    @Column(name = "user_id")
    private Long userId;

    public Transaction() {
    }

    public Transaction(LocalDate date, String type, String category, double amount, String note) {
        this.date = date;
        this.type = type;
        this.category = category;
        this.amount = Math.round(amount * 100.0) / 100.0;
        this.note = note == null ? "" : note.trim();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
