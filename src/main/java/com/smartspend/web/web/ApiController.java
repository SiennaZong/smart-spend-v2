package com.smartspend.web.web;

import com.smartspend.web.entity.Budget;
import com.smartspend.web.entity.Transaction;
import com.smartspend.web.model.Category;
import com.smartspend.web.repo.BudgetRepository;
import com.smartspend.web.repo.TransactionRepository;
import com.smartspend.web.service.AiAnalysisService;
import com.smartspend.web.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 统一 REST API 控制器，所有数据均按当前登录用户隔离
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransactionRepository txRepo;
    private final BudgetRepository budgetRepo;
    private final StatsService stats;
    private final AiAnalysisService ai;

    public ApiController(TransactionRepository txRepo, BudgetRepository budgetRepo,
                         StatsService stats, AiAnalysisService ai) {
        this.txRepo = txRepo;
        this.budgetRepo = budgetRepo;
        this.stats = stats;
        this.ai = ai;
    }

    /** 解析月份参数，空则返回当前月 */
    private static YearMonth parseMonth(String month) {
        if (month == null || month.trim().isEmpty()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("月份格式错误，应为 yyyy-MM：" + month);
        }
    }

    // ==================== 分类 ====================

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Category c : Category.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name());
            m.put("icon", c.getIcon());
            m.put("type", c.getType().name());
            list.add(m);
        }
        return list;
    }

    // ==================== 交易记录 ====================

    @GetMapping("/transactions")
    public Map<String, Object> transactions(@RequestAttribute("userId") Long userId,
                                            @RequestParam(required = false) String month) {
        YearMonth ym = parseMonth(month);
        List<Transaction> list = stats.transactionsOfMonth(userId, ym);
        StatsService.PeriodSummary summary = stats.summaryForMonth(userId, ym);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Transaction tx : list) {
            Category c = Category.fromName(tx.getCategory());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", tx.getId());
            m.put("date", tx.getDate().toString());
            m.put("type", tx.getType());
            m.put("category", tx.getCategory());
            m.put("icon", c != null ? c.getIcon() : "📦");
            m.put("amount", tx.getAmount());
            m.put("note", tx.getNote());
            rows.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", ym.toString());
        result.put("list", rows);
        result.put("summary", summaryMap(summary));
        return result;
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> addTransaction(@RequestBody Map<String, Object> body,
                                            @RequestAttribute("userId") Long userId) {
        try {
            LocalDate date = LocalDate.parse(String.valueOf(body.get("date")).trim());
            String categoryName = String.valueOf(body.get("category")).trim();
            Category category = Category.fromName(categoryName);
            if (category == null) {
                return badRequest("未知分类：" + categoryName);
            }
            double amount = Double.parseDouble(String.valueOf(body.get("amount")).trim());
            if (amount <= 0) {
                return badRequest("金额必须大于 0");
            }
            String note = body.get("note") == null ? "" : String.valueOf(body.get("note"));
            Transaction tx = new Transaction(date, category.getType().name(),
                    category.name(), amount, note);
            tx.setUserId(userId);
            txRepo.save(tx);
            return ResponseEntity.ok(Collections.singletonMap("ok", true));
        } catch (DateTimeParseException e) {
            return badRequest("日期格式错误，应为 yyyy-MM-dd");
        } catch (NumberFormatException e) {
            return badRequest("金额格式错误");
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    /** 编辑记录：回填更新 */
    @PutMapping("/transactions/{id}")
    public ResponseEntity<?> updateTransaction(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body,
                                               @RequestAttribute("userId") Long userId) {
        Transaction tx = txRepo.findById(id).orElse(null);
        if (tx == null || !userId.equals(tx.getUserId())) {
            return badRequest("记录不存在或无权操作");
        }
        try {
            LocalDate date = LocalDate.parse(String.valueOf(body.get("date")).trim());
            String categoryName = String.valueOf(body.get("category")).trim();
            Category category = Category.fromName(categoryName);
            if (category == null) {
                return badRequest("未知分类：" + categoryName);
            }
            double amount = Double.parseDouble(String.valueOf(body.get("amount")).trim());
            if (amount <= 0) {
                return badRequest("金额必须大于 0");
            }
            String note = body.get("note") == null ? "" : String.valueOf(body.get("note"));
            tx.setDate(date);
            tx.setType(category.getType().name());
            tx.setCategory(category.name());
            tx.setAmount(Math.round(amount * 100.0) / 100.0);
            tx.setNote(note.trim());
            txRepo.save(tx);
            return ResponseEntity.ok(Collections.singletonMap("ok", true));
        } catch (DateTimeParseException e) {
            return badRequest("日期格式错误，应为 yyyy-MM-dd");
        } catch (NumberFormatException e) {
            return badRequest("金额格式错误");
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long id,
                                               @RequestAttribute("userId") Long userId) {
        Transaction tx = txRepo.findById(id).orElse(null);
        if (tx == null || !userId.equals(tx.getUserId())) {
            return badRequest("记录不存在或无权操作");
        }
        txRepo.delete(tx);
        return ResponseEntity.ok(Collections.singletonMap("ok", true));
    }

    // ==================== 导出 ====================

    /** 导出当前用户全部记录为 CSV（带 BOM，Excel 打开中文不乱码） */
    @GetMapping("/export")
    public void exportCsv(@RequestAttribute("userId") Long userId,
                          HttpServletResponse response) throws IOException {
        List<Transaction> list = txRepo.findByUserIdOrderByDateDesc(userId);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=smartspend-transactions.csv");
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("日期,类型,分类,金额,备注\r\n");
        for (Transaction tx : list) {
            sb.append(tx.getDate()).append(',')
                    .append("INCOME".equals(tx.getType()) ? "收入" : "支出").append(',')
                    .append(escapeCsv(tx.getCategory())).append(',')
                    .append(String.format("%.2f", tx.getAmount())).append(',')
                    .append(escapeCsv(tx.getNote() == null ? "" : tx.getNote()))
                    .append("\r\n");
        }
        response.getWriter().write(sb.toString());
    }

    /** 含逗号、引号或换行的字段用双引号包裹 */
    private static String escapeCsv(String s) {
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ==================== 统计 ====================

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestAttribute("userId") Long userId,
                                     @RequestParam(required = false) String month) {
        YearMonth ym = parseMonth(month);

        StatsService.PeriodSummary summary = stats.summaryForMonth(userId, ym);
        Map<Category, Double> expenseByCat = stats.expenseByCategory(userId, ym);

        // 支出分类汇总（按金额降序）
        List<Map.Entry<Category, Double>> sorted = new ArrayList<>(expenseByCat.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double total = 0;
        for (Map.Entry<Category, Double> e : sorted) {
            total += e.getValue();
        }
        List<Map<String, Object>> catRows = new ArrayList<>();
        for (Map.Entry<Category, Double> e : sorted) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", e.getKey().name());
            m.put("icon", e.getKey().getIcon());
            m.put("amount", e.getValue());
            m.put("pct", total > 0 ? e.getValue() / total * 100 : 0);
            catRows.add(m);
        }

        // 近 6 个月趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<YearMonth, StatsService.PeriodSummary> e
                : stats.monthlyTrend(userId, 6).entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", e.getKey().toString());
            m.put("income", e.getValue().totalIncome);
            m.put("expense", e.getValue().totalExpense);
            m.put("balance", e.getValue().balance);
            trend.add(m);
        }

        // 预算使用率
        List<Map<String, Object>> budgetRows = new ArrayList<>();
        for (StatsService.BudgetUsage u : stats.budgetUsageForMonth(userId, ym)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", u.category);
            m.put("icon", u.icon);
            m.put("spent", u.spent);
            m.put("limit", u.limit);
            m.put("usagePercent", u.usagePercent);
            m.put("over", u.isOverBudget());
            budgetRows.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", ym.toString());
        result.put("summary", summaryMap(summary));
        result.put("expenseByCategory", catRows);
        result.put("trend", trend);
        result.put("budgetUsage", budgetRows);
        result.put("anomalies", stats.detectAnomalies(userId, ym));
        result.put("dailyExpense", stats.dailyExpense(userId, ym));
        return result;
    }

    // ==================== 预算 ====================

    @GetMapping("/budgets")
    public Map<String, Object> budgets(@RequestAttribute("userId") Long userId,
                                       @RequestParam(required = false) String month) {
        YearMonth ym = parseMonth(month);
        String monthKey = ym.toString();

        Map<String, Double> limitMap = new LinkedHashMap<>();
        for (Budget b : budgetRepo.findByUserIdAndMonth(userId, monthKey)) {
            limitMap.put(b.getCategory(), b.getMonthlyLimit());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Category cat : Category.getByType(Category.TransactionType.EXPENSE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", cat.name());
            m.put("icon", cat.getIcon());
            m.put("limit", limitMap.getOrDefault(cat.name(), 0.0));
            rows.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", monthKey);
        result.put("list", rows);
        return result;
    }

    @PostMapping("/budgets")
    public ResponseEntity<?> saveBudget(@RequestBody Map<String, Object> body,
                                        @RequestAttribute("userId") Long userId) {
        try {
            String month = parseMonth(String.valueOf(body.get("month"))).toString();
            String categoryName = String.valueOf(body.get("category")).trim();
            Category category = Category.fromName(categoryName);
            if (category == null || category.getType() != Category.TransactionType.EXPENSE) {
                return badRequest("无效的支出分类：" + categoryName);
            }
            double limit = Double.parseDouble(String.valueOf(body.get("limit")).trim());
            if (limit < 0) {
                return badRequest("预算金额不能为负数");
            }
            if (limit == 0) {
                budgetRepo.deleteByUserIdAndMonthAndCategory(userId, month, category.name());
            } else {
                Budget budget = budgetRepo.findByUserIdAndMonthAndCategory(userId, month, category.name())
                        .orElse(new Budget());
                budget.setUserId(userId);
                budget.setMonth(month);
                budget.setCategory(category.name());
                budget.setMonthlyLimit(limit);
                budgetRepo.save(budget);
            }
            return ResponseEntity.ok(Collections.singletonMap("ok", true));
        } catch (DateTimeParseException e) {
            return badRequest("月份格式错误，应为 yyyy-MM");
        } catch (NumberFormatException e) {
            return badRequest("预算金额格式错误");
        }
    }

    // ==================== AI 分析 ====================

    @PostMapping("/ai/analyze")
    public ResponseEntity<?> analyze(@RequestAttribute("userId") Long userId,
                                     @RequestParam(required = false) String month) {
        try {
            YearMonth ym = parseMonth(month);
            String report = ai.analyze(ym, userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("month", ym.toString());
            result.put("report", report);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return badRequest("AI 分析失败：" + e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Map<String, Object> summaryMap(StatsService.PeriodSummary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("income", s.totalIncome);
        m.put("expense", s.totalExpense);
        m.put("balance", s.balance);
        return m;
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Collections.singletonMap("error", message));
    }
}
