package com.smartspend.web.web;

import com.smartspend.web.entity.AppSetting;
import com.smartspend.web.entity.Budget;
import com.smartspend.web.entity.Transaction;
import com.smartspend.web.model.Category;
import com.smartspend.web.repo.AppSettingRepository;
import com.smartspend.web.repo.BudgetRepository;
import com.smartspend.web.repo.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 启动时自动把桌面版 CSV 数据迁移到数据库（仅当数据库为空且 CSV 文件存在时执行）
 */
@Component
public class CsvImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CsvImportRunner.class);

    private final TransactionRepository txRepo;
    private final BudgetRepository budgetRepo;
    private final AppSettingRepository settingRepo;

    public CsvImportRunner(TransactionRepository txRepo, BudgetRepository budgetRepo,
                           AppSettingRepository settingRepo) {
        this.txRepo = txRepo;
        this.budgetRepo = budgetRepo;
        this.settingRepo = settingRepo;
    }

    @Override
    public void run(String... args) {
        try {
            importTransactions();
            importBudgets();
            importConfig();
        } catch (Exception e) {
            log.warn("桌面版数据迁移跳过: {}", e.getMessage());
        }
    }

    private void importTransactions() throws Exception {
        Path path = Paths.get("smartspend_data", "transactions.csv");
        if (!Files.exists(path) || txRepo.count() > 0) {
            return;
        }
        List<Transaction> list = new ArrayList<>();
        for (String line : readLines(path)) {
            String[] parts = parseCsv(line);
            if (parts.length < 4) {
                continue;
            }
            Category category = Category.fromName(parts[2]);
            if (category == null) {
                continue;
            }
            try {
                list.add(new Transaction(LocalDate.parse(parts[0]), category.getType().name(),
                        category.name(), Double.parseDouble(parts[3]),
                        parts.length > 4 ? parts[4] : ""));
            } catch (Exception ignored) {
            }
        }
        if (!list.isEmpty()) {
            txRepo.saveAll(list);
            log.info("已从桌面版迁移 {} 条交易记录", list.size());
        }
    }

    private void importBudgets() throws Exception {
        Path path = Paths.get("smartspend_data", "budgets.csv");
        if (!Files.exists(path) || budgetRepo.count() > 0) {
            return;
        }
        String month = YearMonth.now().toString();
        List<Budget> list = new ArrayList<>();
        for (String line : readLines(path)) {
            String[] parts = parseCsv(line);
            if (parts.length < 2) {
                continue;
            }
            Category category = Category.fromName(parts[0]);
            if (category == null) {
                continue;
            }
            try {
                list.add(new Budget(month, category.name(), Double.parseDouble(parts[1])));
            } catch (Exception ignored) {
            }
        }
        if (!list.isEmpty()) {
            budgetRepo.saveAll(list);
            log.info("已从桌面版迁移 {} 条预算（计入当月 {}）", list.size(), month);
        }
    }

    private void importConfig() throws Exception {
        Path path = Paths.get("smartspend_data", "config.properties");
        if (!Files.exists(path) || settingRepo.count() > 0) {
            return;
        }
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        saveIfPresent(props, "api.endpoint", "api.endpoint");
        saveIfPresent(props, "api.key", "api.key");
        saveIfPresent(props, "api.model", "api.model");
    }

    private void saveIfPresent(Properties props, String sourceKey, String targetKey) {
        String value = props.getProperty(sourceKey, "").trim();
        if (!value.isEmpty()) {
            // userId 为 null，待首个注册用户认领
            settingRepo.save(new AppSetting(null, targetKey, value));
        }
    }

    private List<String> readLines(Path path) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /** 手写 CSV 行解析（支持引号转义），与桌面版 Transaction.parseCsv 逻辑一致 */
    private static String[] parseCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }
}
