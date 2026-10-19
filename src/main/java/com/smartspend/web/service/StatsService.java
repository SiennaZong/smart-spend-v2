package com.smartspend.web.service;

import com.smartspend.web.entity.Budget;
import com.smartspend.web.entity.Transaction;
import com.smartspend.web.model.Category;
import com.smartspend.web.repo.BudgetRepository;
import com.smartspend.web.repo.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计计算服务，逻辑移植自桌面版 StatsService，所有查询均限定在单个用户的数据范围内
 */
@Service
public class StatsService {

    private final TransactionRepository txRepo;
    private final BudgetRepository budgetRepo;

    public StatsService(TransactionRepository txRepo, BudgetRepository budgetRepo) {
        this.txRepo = txRepo;
        this.budgetRepo = budgetRepo;
    }

    public static class PeriodSummary {
        public double totalIncome;
        public double totalExpense;
        public double balance;

        public PeriodSummary(double totalIncome, double totalExpense) {
            this.totalIncome = totalIncome;
            this.totalExpense = totalExpense;
            this.balance = totalIncome - totalExpense;
        }
    }

    public static class BudgetUsage {
        public String category;
        public String icon;
        public double spent;
        public double limit;
        public double usagePercent;

        public BudgetUsage(Category category, double spent, double limit) {
            this.category = category.name();
            this.icon = category.getIcon();
            this.spent = spent;
            this.limit = limit;
            this.usagePercent = limit > 0 ? (spent / limit) * 100.0 : 0;
        }

        public boolean isOverBudget() {
            return limit > 0 && spent > limit;
        }
    }

    public List<Transaction> transactionsOfMonth(Long userId, YearMonth month) {
        return txRepo.findByUserIdAndDateBetweenOrderByDateDesc(
                userId, month.atDay(1), month.atEndOfMonth());
    }

    public PeriodSummary summaryForMonth(Long userId, YearMonth month) {
        double income = 0;
        double expense = 0;
        for (Transaction tx : transactionsOfMonth(userId, month)) {
            if (Category.TransactionType.INCOME.name().equals(tx.getType())) {
                income += tx.getAmount();
            } else {
                expense += tx.getAmount();
            }
        }
        return new PeriodSummary(income, expense);
    }

    public Map<Category, Double> expenseByCategory(Long userId, YearMonth month) {
        Map<Category, Double> map = new LinkedHashMap<>();
        for (Transaction tx : transactionsOfMonth(userId, month)) {
            if (Category.TransactionType.EXPENSE.name().equals(tx.getType())) {
                Category c = Category.fromName(tx.getCategory());
                if (c != null) {
                    map.merge(c, tx.getAmount(), Double::sum);
                }
            }
        }
        return map;
    }

    public Map<YearMonth, PeriodSummary> monthlyTrend(Long userId, int months) {
        Map<YearMonth, PeriodSummary> trend = new LinkedHashMap<>();
        YearMonth current = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            trend.put(ym, summaryForMonth(userId, ym));
        }
        return trend;
    }

    public List<BudgetUsage> budgetUsageForMonth(Long userId, YearMonth month) {
        List<BudgetUsage> result = new ArrayList<>();
        Map<Category, Double> spentMap = expenseByCategory(userId, month);
        String monthKey = month.toString();
        for (Category cat : Category.getByType(Category.TransactionType.EXPENSE)) {
            double spent = spentMap.getOrDefault(cat, 0.0);
            double limit = budgetRepo.findByUserIdAndMonthAndCategory(userId, monthKey, cat.name())
                    .map(Budget::getMonthlyLimit).orElse(0.0);
            if (limit > 0 || spent > 0) {
                result.add(new BudgetUsage(cat, spent, limit));
            }
        }
        return result;
    }

    /** 某月每日支出汇总，key 为 yyyy-MM-dd 字符串 */
    public Map<String, Double> dailyExpense(Long userId, YearMonth month) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (Transaction tx : transactionsOfMonth(userId, month)) {
            if (Category.TransactionType.EXPENSE.name().equals(tx.getType())) {
                String day = tx.getDate().toString();
                map.merge(day, tx.getAmount(), Double::sum);
            }
        }
        return map;
    }

    public List<String> detectAnomalies(Long userId, YearMonth month) {
        List<String> warnings = new ArrayList<>();
        Map<Category, Double> current = expenseByCategory(userId, month);
        Map<Category, Double> prev = expenseByCategory(userId, month.minusMonths(1));
        String monthKey = month.toString();

        double totalCurrent = 0;
        for (double v : current.values()) {
            totalCurrent += v;
        }
        double totalPrev = 0;
        for (double v : prev.values()) {
            totalPrev += v;
        }

        if (totalPrev > 0 && totalCurrent > totalPrev * 1.3) {
            warnings.add(String.format("本月总支出 %.2f 元，较上月增长 %.0f%%，请注意控制消费。",
                    totalCurrent, (totalCurrent / totalPrev - 1) * 100));
        }

        for (Map.Entry<Category, Double> e : current.entrySet()) {
            double cur = e.getValue();
            double pre = prev.getOrDefault(e.getKey(), 0.0);
            if (pre > 0 && cur > pre * 1.5 && cur > 100) {
                warnings.add(String.format("%s本月支出 %.2f 元，较上月增长 %.0f%%，可能存在异常消费。",
                        e.getKey().getIcon() + e.getKey().name(), cur, (cur / pre - 1) * 100));
            }
            double limit = budgetRepo.findByUserIdAndMonthAndCategory(userId, monthKey, e.getKey().name())
                    .map(Budget::getMonthlyLimit).orElse(0.0);
            if (limit > 0 && cur > limit) {
                warnings.add(String.format("%s已超出预算 %.2f 元（预算 %.2f）。",
                        e.getKey().getIcon() + e.getKey().name(), cur - limit, limit));
            }
        }
        return warnings;
    }
}
