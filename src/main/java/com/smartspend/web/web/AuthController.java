package com.smartspend.web.web;

import com.smartspend.web.entity.AppSetting;
import com.smartspend.web.entity.Budget;
import com.smartspend.web.entity.Transaction;
import com.smartspend.web.entity.User;
import com.smartspend.web.repo.AppSettingRepository;
import com.smartspend.web.repo.BudgetRepository;
import com.smartspend.web.repo.TransactionRepository;
import com.smartspend.web.repo.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册 / 登录 / 登出认证接口，基于 HttpSession 会话认证
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final TransactionRepository txRepo;
    private final BudgetRepository budgetRepo;
    private final AppSettingRepository settingRepo;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepo, TransactionRepository txRepo,
                          BudgetRepository budgetRepo, AppSettingRepository settingRepo) {
        this.userRepo = userRepo;
        this.txRepo = txRepo;
        this.budgetRepo = budgetRepo;
        this.settingRepo = settingRepo;
    }

    /** 最大允许注册用户数（个人使用，防止陌生人注册产生费用） */
    private static final int MAX_USERS = 1;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body, HttpSession session) {
        if (userRepo.count() >= MAX_USERS) {
            return badRequest("注册已关闭，当前为个人使用模式");
        }
        String username = str(body.get("username")).trim();
        String password = str(body.get("password"));
        if (username.isEmpty() || username.length() > 20) {
            return badRequest("用户名需为 1~20 个字符");
        }
        if (!username.matches("[\\u4e00-\\u9fa5A-Za-z0-9_]+")) {
            return badRequest("用户名仅支持中文、字母、数字和下划线");
        }
        if (password.length() < 6 || password.length() > 40) {
            return badRequest("密码长度需为 6~40 位");
        }
        if (userRepo.existsByUsername(username)) {
            return badRequest("用户名已被注册");
        }
        User user = userRepo.save(new User(username, encoder.encode(password)));
        claimLegacyData(user.getId());
        bindSession(session, user);
        return ResponseEntity.ok(userMap(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body, HttpSession session) {
        String username = str(body.get("username")).trim();
        String password = str(body.get("password"));
        User user = userRepo.findByUsername(username).orElse(null);
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            return badRequest("用户名或密码错误");
        }
        bindSession(session, user);
        return ResponseEntity.ok(userMap(user));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Collections.singletonMap("ok", true);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401)
                    .body(Collections.singletonMap("error", "未登录"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("username", session.getAttribute("username"));
        return ResponseEntity.ok(m);
    }

    // ==================== 私有方法 ====================

    private void bindSession(HttpSession session, User user) {
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
    }

    /** 首个注册用户自动认领历史迁移数据（userId 为空的记录） */
    private void claimLegacyData(Long userId) {
        if (userRepo.count() != 1) {
            return;
        }
        List<Transaction> txs = txRepo.findByUserIdIsNull();
        for (Transaction t : txs) {
            t.setUserId(userId);
        }
        txRepo.saveAll(txs);
        List<Budget> budgets = budgetRepo.findByUserIdIsNull();
        for (Budget b : budgets) {
            b.setUserId(userId);
        }
        budgetRepo.saveAll(budgets);
        List<AppSetting> settings = settingRepo.findByUserIdIsNull();
        for (AppSetting s : settings) {
            s.setUserId(userId);
        }
        settingRepo.saveAll(settings);
    }

    private Map<String, Object> userMap(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", user.getId());
        m.put("username", user.getUsername());
        return m;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Collections.singletonMap("error", message));
    }
}
