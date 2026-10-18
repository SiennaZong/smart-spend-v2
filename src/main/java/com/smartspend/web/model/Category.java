package com.smartspend.web.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 收支分类枚举，与桌面版保持一致
 */
public enum Category {

    餐饮(TransactionType.EXPENSE, "🍚"),
    交通(TransactionType.EXPENSE, "🚌"),
    购物(TransactionType.EXPENSE, "🛒"),
    娱乐(TransactionType.EXPENSE, "🎮"),
    居住(TransactionType.EXPENSE, "🏠"),
    医疗(TransactionType.EXPENSE, "💊"),
    教育(TransactionType.EXPENSE, "📚"),
    通讯(TransactionType.EXPENSE, "📱"),
    日用(TransactionType.EXPENSE, "🧴"),
    其他支出(TransactionType.EXPENSE, "📦"),

    工资(TransactionType.INCOME, "💰"),
    兼职(TransactionType.INCOME, "💼"),
    理财(TransactionType.INCOME, "📈"),
    红包(TransactionType.INCOME, "🧧"),
    其他收入(TransactionType.INCOME, "✨");

    public enum TransactionType {
        INCOME("收入"),
        EXPENSE("支出");

        private final String label;

        TransactionType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final TransactionType type;
    private final String icon;

    Category(TransactionType type, String icon) {
        this.type = type;
        this.icon = icon;
    }

    public TransactionType getType() {
        return type;
    }

    public String getIcon() {
        return icon;
    }

    public static List<Category> getByType(TransactionType type) {
        List<Category> result = new ArrayList<>();
        for (Category c : values()) {
            if (c.type == type) {
                result.add(c);
            }
        }
        return result;
    }

    public static Category fromName(String name) {
        if (name == null) {
            return null;
        }
        for (Category c : values()) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }
}
