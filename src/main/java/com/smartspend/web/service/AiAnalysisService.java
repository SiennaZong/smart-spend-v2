package com.smartspend.web.service;

import com.smartspend.web.entity.AppSetting;
import com.smartspend.web.repo.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 消费分析服务，逻辑移植自桌面版 AIAnalysisService，
 * API 配置从数据库 app_settings 表读取
 */
@Service
public class AiAnalysisService {

    private static final String SYSTEM_PROMPT =
            "你是一位专业的个人理财顾问，擅长分析消费习惯并给出实用建议。" +
            "请根据用户提供的月度收支数据，生成一份中文消费分析报告，要求：\n" +
            "1. 用简洁的语言总结本月整体收支情况；\n" +
            "2. 分析主要消费类别及其占比，指出消费结构是否合理；\n" +
            "3. 结合预算执行情况，提醒超支或值得关注的类别；\n" +
            "4. 给出 2-3 条具体可执行的优化建议；\n" +
            "5. 语气友好、鼓励，避免说教。";

    private static final String KEY_ENDPOINT = "api.endpoint";
    private static final String KEY_API_KEY = "api.key";
    private static final String KEY_MODEL = "api.model";

    private final StatsService stats;
    private final AppSettingRepository settingRepo;

    @Value("${app.ai.default-endpoint}")
    private String defaultEndpoint;

    @Value("${app.ai.default-model}")
    private String defaultModel;

    public AiAnalysisService(StatsService stats, AppSettingRepository settingRepo) {
        this.stats = stats;
        this.settingRepo = settingRepo;
    }

    public String getEndpoint(Long userId) {
        return settingRepo.findByUserIdAndSettingKey(userId, KEY_ENDPOINT)
                .map(AppSetting::getSettingValue).orElse(defaultEndpoint);
    }

    public String getModel(Long userId) {
        return settingRepo.findByUserIdAndSettingKey(userId, KEY_MODEL)
                .map(AppSetting::getSettingValue).orElse(defaultModel);
    }

    public String getApiKey(Long userId) {
        return settingRepo.findByUserIdAndSettingKey(userId, KEY_API_KEY)
                .map(AppSetting::getSettingValue).orElse("");
    }

    public void saveSettings(Long userId, String endpoint, String apiKey, String model) {
        save(userId, KEY_ENDPOINT, endpoint);
        save(userId, KEY_API_KEY, apiKey);
        save(userId, KEY_MODEL, model);
    }

    private void save(Long userId, String key, String value) {
        if (value == null) {
            value = "";
        }
        AppSetting setting = settingRepo.findByUserIdAndSettingKey(userId, key)
                .orElse(new AppSetting(userId, key, ""));
        setting.setSettingValue(value.trim());
        settingRepo.save(setting);
    }

    public String analyze(YearMonth month, Long userId) throws Exception {
        String endpoint = getEndpoint(userId);
        String key = getApiKey(userId);
        String model = getModel(userId);

        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalStateException("未配置 API 地址");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("未配置 API Key，请在设置中填写");
        }

        String userMessage = buildUserMessage(month, userId);
        String body = buildRequestBody(model, userMessage);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String response = readStream(is);

            if (code < 200 || code >= 300) {
                throw new RuntimeException("API 请求失败 (HTTP " + code + "): " + response);
            }

            String content = extractContent(response);
            if (content == null || content.isEmpty()) {
                throw new RuntimeException("API 返回内容为空，原始响应: " + response);
            }
            return content;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String buildUserMessage(YearMonth month, Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析 ").append(month).append(" 的个人消费情况：\n\n");

        StatsService.PeriodSummary summary = stats.summaryForMonth(userId, month);
        sb.append("【本月概览】\n");
        sb.append("总收入: ").append(String.format("%.2f", summary.totalIncome)).append(" 元\n");
        sb.append("总支出: ").append(String.format("%.2f", summary.totalExpense)).append(" 元\n");
        sb.append("结余: ").append(String.format("%.2f", summary.balance)).append(" 元\n\n");

        Map<com.smartspend.web.model.Category, Double> expense = stats.expenseByCategory(userId, month);
        sb.append("【支出明细（按分类）】\n");
        if (expense.isEmpty()) {
            sb.append("本月暂无支出记录。\n");
        } else {
            double total = expense.values().stream().mapToDouble(Double::doubleValue).sum();
            List<Map.Entry<com.smartspend.web.model.Category, Double>> sorted = expense.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .collect(Collectors.toList());
            for (Map.Entry<com.smartspend.web.model.Category, Double> e : sorted) {
                double pct = total > 0 ? e.getValue() / total * 100 : 0;
                sb.append(e.getKey().getIcon()).append(e.getKey().name())
                        .append(": ").append(String.format("%.2f", e.getValue()))
                        .append(" 元 (").append(String.format("%.1f", pct)).append("%)\n");
            }
        }
        sb.append("\n");

        List<StatsService.BudgetUsage> usages = stats.budgetUsageForMonth(userId, month);
        sb.append("【预算执行情况】\n");
        boolean hasBudget = false;
        for (StatsService.BudgetUsage u : usages) {
            if (u.limit > 0) {
                hasBudget = true;
                String status = u.isOverBudget() ? "已超支" : "正常";
                sb.append(u.icon).append(u.category)
                        .append(": 已用 ").append(String.format("%.2f", u.spent))
                        .append(" / 预算 ").append(String.format("%.2f", u.limit))
                        .append(" (").append(String.format("%.0f", u.usagePercent)).append("%) ")
                        .append(status).append("\n");
            }
        }
        if (!hasBudget) {
            sb.append("尚未设置任何分类预算。\n");
        }
        sb.append("\n");

        List<String> anomalies = stats.detectAnomalies(userId, month);
        if (!anomalies.isEmpty()) {
            sb.append("【系统检测到的异常】\n");
            for (String a : anomalies) {
                sb.append("- ").append(a).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildRequestBody(String model, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(escapeJson(model)).append("\",");
        sb.append("\"temperature\":0.7,");
        sb.append("\"messages\":[");
        sb.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(SYSTEM_PROMPT)).append("\"},");
        sb.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(userMessage)).append("\"}");
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private String extractContent(String json) {
        String key = "\"content\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            key = "\"content\": \"";
            start = json.indexOf(key);
            if (start < 0) {
                return null;
            }
        }
        start += key.length();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                        break;
                    default: sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
