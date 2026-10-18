/* ===== SmartSpend Web 前端逻辑 ===== */

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

const fmtMoney = (v) => "¥" + Number(v).toFixed(2);
const TYPE_LABEL = { INCOME: "收入", EXPENSE: "支出" };
const MONTH_FMT = { year: "numeric", month: "2-digit" };

let pieChart = null;
let barChart = null;
let categories = [];
let currentUser = null;
let txCache = [];
let editingId = null;
let filterType = "ALL";
let searchText = "";
let fullTxCache = [];
let fullStatsCache = null;
let fullBudgetCache = null;

/* ---------- 主题切换 ---------- */
function initTheme() {
    const saved = localStorage.getItem("smartspend-theme");
    if (saved) applyTheme(saved);
    else if (window.matchMedia("(prefers-color-scheme: dark)").matches) applyTheme("dark");
}
function applyTheme(t) {
    document.documentElement.setAttribute("data-theme", t);
    $("#btnTheme").textContent = t === "dark" ? "☀️" : "🌙";
    localStorage.setItem("smartspend-theme", t);
    if (pieChart) renderPieChartTheme();
    if (barChart) renderBarChartTheme();
}
$("#btnTheme").addEventListener("click", () => {
    const cur = document.documentElement.getAttribute("data-theme") || "light";
    applyTheme(cur === "dark" ? "light" : "dark");
});

/* ---------- 通用请求 ---------- */
async function api(url, options) {
    const resp = await fetch(url, options);
    if (resp.status === 401) {
        showLogin();
        throw new Error("请先登录");
    }
    const data = await resp.json().catch(() => ({}));
    if (!resp.ok) {
        throw new Error(data.error || ("请求失败 (HTTP " + resp.status + ")"));
    }
    return data;
}

/* ---------- 登录 / 注册 ---------- */
function showLogin() {
    currentUser = null;
    $("#loginMask").hidden = false;
    $("#userBox").hidden = true;
    showAuthView("login");
}

function enterApp(user) {
    currentUser = user;
    $("#loginMask").hidden = true;
    $("#userBox").hidden = false;
    $("#displayName").textContent = user.username;
}

async function checkAuth() {
    try {
        const resp = await fetch("/api/auth/me");
        if (resp.ok) {
            enterApp(await resp.json());
            return true;
        }
    } catch (e) { /* 网络异常时仍显示登录框 */ }
    showLogin();
    return false;
}

function authError(msg, view) {
    const el = $(view === "register" ? "#regError" : "#loginError");
    if (!msg) { el.hidden = true; el.textContent = ""; return null; }
    el.textContent = msg;
    el.hidden = false;
}

function clearAuthError() {
    ["#loginError", "#regError"].forEach((s) => {
        const el = $(s);
        if (el) { el.hidden = true; el.textContent = ""; }
    });
}

/* 在登录视图与注册视图之间切换 */
function showAuthView(view) {
    clearAuthError();
    const isReg = view === "register";
    $("#loginView").hidden = isReg;
    $("#registerView").hidden = !isReg;
    if (isReg) {
        $("#regUsername").focus();
    } else {
        $("#loginUsername").focus();
    }
}

async function doLogin() {
    const btn = $("#btnLogin");
    const oldText = btn.textContent;
    clearAuthError();
    const username = $("#loginUsername").value.trim();
    const password = $("#loginPassword").value;
    if (!username) { authError("请输入用户名", "login"); return; }
    if (!password) { authError("请输入密码", "login"); return; }
    btn.disabled = true;
    btn.textContent = "处理中…";
    try {
        const user = await api("/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password })
        });
        enterApp(user);
        $("#loginPassword").value = "";
        toast("登录成功，欢迎回来！", "success");
        await initAppData();
    } catch (err) {
        authError(err.message, "login");
        toast(err.message, "error");
    } finally {
        btn.disabled = false;
        btn.textContent = oldText;
    }
}

async function doRegister() {
    const btn = $("#btnDoRegister");
    const oldText = btn.textContent;
    clearAuthError();
    const username = $("#regUsername").value.trim();
    const password = $("#regPassword").value;
    const confirm = $("#regConfirm").value;
    if (!username) { authError("请输入新用户名", "register"); return; }
    if (!password) { authError("请输入密码", "register"); return; }
    if (password.length < 6) { authError("密码至少 6 位", "register"); return; }
    if (password !== confirm) { authError("两次输入的密码不一致", "register"); return; }
    btn.disabled = true;
    btn.textContent = "处理中…";
    try {
        const user = await api("/api/auth/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password })
        });
        enterApp(user);
        $("#regPassword").value = "";
        $("#regConfirm").value = "";
        toast("注册成功，已自动登录！", "success");
        await initAppData();
    } catch (err) {
        authError(err.message, "register");
        toast(err.message, "error");
    } finally {
        btn.disabled = false;
        btn.textContent = oldText;
    }
}

$("#btnLogin").addEventListener("click", doLogin);
$("#btnGoRegister").addEventListener("click", () => showAuthView("register"));
$("#btnDoRegister").addEventListener("click", doRegister);
$("#btnBackLogin").addEventListener("click", () => showAuthView("login"));
$("#loginUsername").addEventListener("keydown", (e) => {
    if (e.key === "Enter") $("#loginPassword").focus();
});
$("#loginPassword").addEventListener("keydown", (e) => {
    if (e.key === "Enter") doLogin();
});
$("#regPassword").addEventListener("keydown", (e) => {
    if (e.key === "Enter") $("#regConfirm").focus();
});
$("#regConfirm").addEventListener("keydown", (e) => {
    if (e.key === "Enter") doRegister();
});
$("#btnLogout").addEventListener("click", async () => {
    try {
        await api("/api/auth/logout", { method: "POST" });
    } catch (e) { /* 忽略 */ }
    showLogin();
    toast("已退出登录", "success");
});

function toast(msg, type) {
    const el = $("#toast");
    el.textContent = msg;
    el.className = "toast " + (type || "");
    el.hidden = false;
    clearTimeout(el._timer);
    el._timer = setTimeout(() => { el.hidden = true; }, 2200);
}

/* ---------- 月份选项：近 12 个月 ---------- */
function monthOptions() {
    const list = [];
    const now = new Date();
    for (let i = 0; i < 12; i++) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        list.push(d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0"));
    }
    return list;
}

function fillMonthSelect(select) {
    const current = select.value;
    select.innerHTML = monthOptions()
        .map((m) => `<option value="${m}">${m}</option>`).join("");
    if (current && monthOptions().includes(current)) {
        select.value = current;
    } else {
        select.value = monthOptions()[0];
    }
}

function fillAllMonthSelects() {
    ["#txMonth", "#statsMonth", "#budgetMonth", "#aiMonth"].forEach((s) => {
        const el = $(s);
        if (el) fillMonthSelect(el);
    });
}

/* ---------- 标签页切换 ---------- */
$$(".tab").forEach((tab) => {
    tab.addEventListener("click", () => {
        $$(".tab").forEach((t) => t.classList.remove("active"));
        $$(".panel").forEach((p) => p.classList.remove("active"));
        tab.classList.add("active");
        $("#tab-" + tab.dataset.tab).classList.add("active");
        if (tab.dataset.tab === "overview") loadOverview();
        if (tab.dataset.tab === "list") loadTransactions();
        if (tab.dataset.tab === "stats") loadStats();
        if (tab.dataset.tab === "budget") loadBudgets();
    });
});

/* ---------- 仪表盘（概览） ---------- */
async function loadOverview() {
    try {
        const month = monthOptions()[0];
        const [stats, budgets] = await Promise.all([
            api("/api/stats?month=" + month),
            api("/api/budgets?month=" + month)
        ]);
        $("#ovIncome").textContent = fmtMoney(stats.summary.income);
        $("#ovExpense").textContent = fmtMoney(stats.summary.expense);
        $("#ovBalance").textContent = fmtMoney(stats.summary.balance);

        const incomeDays = fullDaysOfMonth();
        const incomeAvg = stats.summary.income / incomeDays;
        const expenseAvg = stats.summary.expense / incomeDays;
        $("#ovIncomeSub").textContent = `日均 ${fmtMoney(incomeAvg)}`;
        $("#ovExpenseSub").textContent = `日均 ${fmtMoney(expenseAvg)}`;

        // 预算执行率
        const budgetList = budgets.list || [];
        let totalLimit = 0, totalSpent = 0;
        const spentMap = {};
        (stats.budgetUsage || []).forEach((u) => { spentMap[u.category] = u.spent; });
        budgetList.forEach((b) => {
            if (b.limit > 0) {
                totalLimit += b.limit;
                totalSpent += spentMap[b.category] || 0;
            }
        });
        if (totalLimit > 0) {
            const pct = Math.round(Math.min(totalSpent / totalLimit * 100, 100));
            $("#ovBudgetPct").textContent = pct + "%";
            $("#ovBudgetPct").style.color = totalSpent > totalLimit ? "var(--expense)" : "var(--primary)";
            $("#ovBudgetSub").textContent = fmtMoney(totalSpent) + " / " + fmtMoney(totalLimit);
        } else {
            $("#ovBudgetPct").textContent = "—";
            $("#ovBudgetPct").style.color = "var(--primary)";
            $("#ovBudgetSub").textContent = "未设置预算";
        }

        // Top3 支出分类
        const top = (stats.expenseByCategory || []).slice(0, 3);
        const maxTop = top.length ? top[0].amount : 1;
        if (top.length === 0) {
            $("#ovTopExpense").innerHTML = `<li style="color:var(--text-muted)">本月暂无支出记录</li>`;
        } else {
            $("#ovTopExpense").innerHTML = top.map((c) => `
                <li>
                    <span class="cat-icon">${c.icon}</span>
                    <span class="cat-name">${c.category}</span>
                    <div class="cat-bar-wrap"><div class="cat-bar" style="width:${c.amount / maxTop * 100}%"></div></div>
                    <span class="cat-amt" style="color:var(--expense)">-${fmtMoney(c.amount).replace("¥","")}</span>
                </li>
            `).join("");
        }

        // 迷你趋势（近 6 个月收支双柱）
        const trend = stats.trend || [];
        const maxTrend = trend.reduce((m, t) => Math.max(m, t.income, t.expense), 0) || 1;
        const barsHtml = trend.map((t) => {
            const incH = Math.max(4, t.income / maxTrend * 70);
            const expH = Math.max(4, t.expense / maxTrend * 70);
            return `
                <div style="display:flex;flex-direction:column;align-items:center;gap:2px;flex:1;position:relative">
                    <div class="bar" style="height:${incH}px;background:#10b981" data-tip="收入 ${fmtMoney(t.income)}"></div>
                    <div class="bar" style="height:${expH}px;background:#ef4444" data-tip="支出 ${fmtMoney(t.expense)}"></div>
                </div>
            `;
        }).join("");
        $("#ovMiniTrend").innerHTML = barsHtml || `<div style="color:var(--text-muted);font-size:13px">暂无数据</div>`;
        $("#ovMiniLabels").innerHTML = trend.map((t) =>
            `<span>${String(t.month).slice(5)}月</span>`
        ).join("") || "";

        // 缓存
        fullStatsCache = stats;
        fullBudgetCache = budgets;
    } catch (err) {
        $("#ovIncome").textContent = "加载失败";
        $("#ovExpense").textContent = "加载失败";
        $("#ovBalance").textContent = "加载失败";
    }
}

function fullDaysOfMonth() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
}

/* ---------- 分类 ---------- */
async function loadCategories() {
    categories = await api("/api/categories");
    refreshCategoryOptions();
}

function currentEntryType() {
    return document.querySelector('input[name="txType"]:checked').value;
}

function refreshCategoryOptions() {
    const type = currentEntryType();
    const sel = $("#category");
    sel.innerHTML = categories
        .filter((c) => c.type === type)
        .map((c) => `<option value="${c.name}">${c.icon} ${c.name}</option>`).join("");
}

$$('input[name="txType"]').forEach((r) => r.addEventListener("change", () => {
    refreshCategoryOptions();
    updateNoteHints();
}));

/* ---------- 录入 ---------- */
$("#entryForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const body = {
        date: $("#date").value,
        category: $("#category").value,
        amount: $("#amount").value,
        note: $("#note").value
    };
    try {
        await api("/api/transactions", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        toast("记录保存成功！", "success");
        $("#amount").value = "";
        $("#note").value = "";
        updateNoteHints();
        checkBudgetWarning(body.category, Number(body.amount));
        if ($("#tab-overview").classList.contains("active")) loadOverview();
        if ($("#tab-list").classList.contains("active")) loadTransactions();
    } catch (err) {
        toast(err.message, "error");
    }
});

/* ---------- 明细 ---------- */
function highlightText(text, keyword) {
    if (!keyword) return escapeHtml(text || "");
    const escaped = escapeHtml(text || "");
    const re = new RegExp("(" + escapeRegExp(keyword) + ")", "gi");
    return escaped.replace(re, '<span class="hl">$1</span>');
}
function escapeRegExp(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); }

async function loadTransactions() {
    const month = $("#txMonth").value;
    const data = await api("/api/transactions?month=" + month);
    fullTxCache = data.list;
    renderTxTable();
}

function renderTxTable() {
    let list = fullTxCache;
    if (filterType !== "ALL") list = list.filter((t) => t.type === filterType);
    if (searchText) {
        const kw = searchText.trim().toLowerCase();
        list = list.filter((t) => (t.note || "").toLowerCase().includes(kw));
    }

    const tbody = $("#txBody");
    tbody.innerHTML = list.map((tx) => {
        const isIn = tx.type === "INCOME";
        const amtCls = isIn ? "amt-in" : "amt-out";
        const amtText = (isIn ? "+" : "-") + Number(tx.amount).toFixed(2);
        return `<tr>
            <td>${tx.date}</td>
            <td>${TYPE_LABEL[tx.type] || tx.type}</td>
            <td>${tx.icon} ${tx.category}</td>
            <td class="num ${amtCls}">${amtText}</td>
            <td>${highlightText(tx.note, searchText)}</td>
            <td class="op"><button class="btn btn-secondary btn-sm" data-edit="${tx.id}">编辑</button><button class="btn btn-danger" data-del="${tx.id}">删除</button></td>
        </tr>`;
    }).join("");
    txCache = list;
    $("#txEmpty").hidden = list.length > 0;

    // 重新计算过滤后的汇总
    const inc = list.filter((t) => t.type === "INCOME").reduce((s, t) => s + t.amount, 0);
    const exp = list.filter((t) => t.type === "EXPENSE").reduce((s, t) => s + t.amount, 0);
    $("#sumIncome").textContent = fmtMoney(inc);
    $("#sumExpense").textContent = fmtMoney(exp);
    $("#sumBalance").textContent = fmtMoney(inc - exp);

    $$("button[data-edit]").forEach((btn) => {
        btn.addEventListener("click", () => {
            const tx = fullTxCache.find((t) => String(t.id) === btn.dataset.edit);
            if (tx) openEditModal(tx);
        });
    });

    $$("button[data-del]").forEach((btn) => {
        btn.addEventListener("click", async () => {
            if (!confirm("确定删除这条记录吗？")) return;
            try {
                await api("/api/transactions/" + btn.dataset.del, { method: "DELETE" });
                toast("已删除", "success");
                loadTransactions();
            } catch (err) {
                toast(err.message, "error");
            }
        });
    });
}

// 快捷筛选
$$("#filterType .filter-pill").forEach((btn) => {
    btn.addEventListener("click", () => {
        $$("#filterType .filter-pill").forEach((b) => b.classList.remove("active"));
        btn.classList.add("active");
        filterType = btn.dataset.type;
        renderTxTable();
    });
});
$("#txSearch").addEventListener("input", (e) => {
    searchText = e.target.value;
    renderTxTable();
});

$("#btnRefreshTx").addEventListener("click", () => loadTransactions().catch((e) => toast(e.message, "error")));
$("#txMonth").addEventListener("change", () => loadTransactions().catch((e) => toast(e.message, "error")));

/* ---------- 编辑记录 ---------- */
function refreshEditCategoryOptions() {
    const type = document.querySelector('input[name="editType"]:checked').value;
    $("#editCategory").innerHTML = categories
        .filter((c) => c.type === type)
        .map((c) => `<option value="${c.name}">${c.icon} ${c.name}</option>`).join("");
}

$$('input[name="editType"]').forEach((r) => r.addEventListener("change", refreshEditCategoryOptions));

function openEditModal(tx) {
    editingId = tx.id;
    document.querySelector(`input[name="editType"][value="${tx.type}"]`).checked = true;
    refreshEditCategoryOptions();
    $("#editCategory").value = tx.category;
    $("#editDate").value = tx.date;
    $("#editAmount").value = tx.amount;
    $("#editNote").value = tx.note || "";
    $("#editModal").hidden = false;
}

$("#btnCloseEdit").addEventListener("click", () => { $("#editModal").hidden = true; });
$("#editModal").addEventListener("click", (e) => {
    if (e.target === $("#editModal")) $("#editModal").hidden = true;
});

$("#btnSaveEdit").addEventListener("click", async () => {
    if (!$("#editDate").value) { toast("请选择日期", "error"); return; }
    try {
        await api("/api/transactions/" + editingId, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                date: $("#editDate").value,
                category: $("#editCategory").value,
                amount: $("#editAmount").value,
                note: $("#editNote").value
            })
        });
        $("#editModal").hidden = true;
        toast("修改已保存！", "success");
        loadTransactions();
    } catch (err) {
        toast(err.message, "error");
    }
});

/* ---------- 导出 CSV ---------- */
$("#btnExport").addEventListener("click", () => {
    window.location.href = "/api/export";
});

/* ---------- 录入智能提示（备注） ---------- */
async function updateNoteHints() {
    try {
        const data = await api("/api/transactions?month=" + monthOptions()[0]);
        fullTxCache = data.list;
        const type = currentEntryType();
        const cat = $("#category").value;
        const filtered = fullTxCache.filter((t) => t.type === type && t.category === cat && t.note);
        const freq = {};
        filtered.forEach((t) => { freq[t.note] = (freq[t.note] || 0) + 1; });
        const top = Object.entries(freq)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5)
            .map(([note]) => note);
        if (top.length > 0) {
            $("#noteHint").hidden = false;
            $("#noteHintList").innerHTML = top.map((n) =>
                `<span class="note-hint-chip" data-note="${escapeHtml(n)}">${escapeHtml(n)}</span>`
            ).join("");
            $$(".note-hint-chip").forEach((chip) => {
                chip.addEventListener("click", () => {
                    $("#note").value = chip.dataset.note;
                });
            });
        } else {
            $("#noteHint").hidden = true;
        }
    } catch (e) { /* 忽略 */ }
}
$("#category").addEventListener("change", updateNoteHints);

/* ---------- 预算超支提醒 ---------- */
async function checkBudgetWarning(category, newAmount) {
    const cat = categories.find(c => c.name === category);
    if (!cat || cat.type !== "EXPENSE") return;
    try {
        const month = monthOptions()[0];
        const [budgetData, statsData] = await Promise.all([
            api("/api/budgets?month=" + month),
            api("/api/stats?month=" + month)
        ]);
        const budget = (budgetData.list || []).find(b => b.category === category);
        if (!budget || !budget.limit || budget.limit <= 0) return;
        const spentMap = {};
        (statsData.budgetUsage || []).forEach(u => { spentMap[u.category] = u.spent; });
        const spent = spentMap[category] || 0;
        if (spent > budget.limit) {
            toast(`⚠️ ${category} 已超支！已用 ¥${spent.toFixed(0)} / 预算 ¥${budget.limit}`, "error");
        } else if (spent > budget.limit * 0.8) {
            toast(`⚠️ ${category} 已用 ${(spent / budget.limit * 100).toFixed(0)}%，接近预算上限`, "warning");
        }
    } catch (e) { /* 忽略 */ }
}

/* ---------- 统计 ---------- */
const PALETTE = ["#6366f1", "#8b5cf6", "#ec4899", "#f59e0b", "#10b981",
    "#3b82f6", "#ef4444", "#14b8a6", "#a855f7", "#64748b"];

function currentChartBorder() {
    const dark = document.documentElement.getAttribute("data-theme") === "dark";
    return dark ? "#1e293b" : "#ffffff";
}
function currentChartText() {
    const dark = document.documentElement.getAttribute("data-theme") === "dark";
    return dark ? "#cbd5e1" : "#64748b";
}

function renderPie(rows) {
    const ctx = $("#pieChart").getContext("2d");
    if (pieChart) pieChart.destroy();
    const borderColor = currentChartBorder();
    if (!rows.length) {
        pieChart = new Chart(ctx, {
            type: "doughnut",
            data: { labels: ["暂无支出"], datasets: [{ data: [1], backgroundColor: ["#e2e8f0"], borderWidth: 2, borderColor }] },
            options: { plugins: { legend: { display: false, labels: { color: currentChartText() } } } }
        });
        return;
    }
    pieChart = new Chart(ctx, {
        type: "doughnut",
        data: {
            labels: rows.map((r) => r.icon + " " + r.category),
            datasets: [{
                data: rows.map((r) => r.amount),
                backgroundColor: rows.map((_, i) => PALETTE[i % PALETTE.length]),
                borderWidth: 2,
                borderColor
            }]
        },
        options: {
            cutout: "55%",
            plugins: {
                legend: { position: "right", labels: { font: { size: 12 }, color: currentChartText() } },
                tooltip: {
                    callbacks: {
                        label: (item) => `${item.label}: ¥${Number(item.parsed).toFixed(2)}`
                    }
                }
            }
        }
    });
}

function renderBar(trend) {
    const ctx = $("#barChart").getContext("2d");
    if (barChart) barChart.destroy();
    barChart = new Chart(ctx, {
        type: "bar",
        data: {
            labels: trend.map((t) => t.month),
            datasets: [
                { label: "收入", data: trend.map((t) => t.income), backgroundColor: "#10b981", borderRadius: 6 },
                { label: "支出", data: trend.map((t) => t.expense), backgroundColor: "#ef4444", borderRadius: 6 }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: "top", labels: { color: currentChartText() } }
            },
            scales: {
                y: { beginAtZero: true, ticks: { color: currentChartText() }, grid: { color: "rgba(148,163,184,.15)" } },
                x: { ticks: { color: currentChartText() }, grid: { color: "rgba(148,163,184,.15)" } }
            }
        }
    });
}

async function loadStats() {
    const month = $("#statsMonth").value;
    const data = await api("/api/stats?month=" + month);

    $("#stIncome").textContent = fmtMoney(data.summary.income);
    $("#stExpense").textContent = fmtMoney(data.summary.expense);
    $("#stBalance").textContent = fmtMoney(data.summary.balance);

    renderPie(data.expenseByCategory);
    renderBar(data.trend);

    const card = $("#anomalyCard");
    const list = $("#anomalyList");
    if (data.anomalies && data.anomalies.length > 0) {
        card.hidden = false;
        list.innerHTML = data.anomalies.map((a) => `<li>${escapeHtml(a)}</li>`).join("");
    } else {
        card.hidden = true;
    }
}

function renderPieChartTheme() { /* 重绘触发 */ }
function renderBarChartTheme() { /* 重绘触发 */ }

$("#btnRefreshStats").addEventListener("click", () => loadStats().catch((e) => toast(e.message, "error")));
$("#statsMonth").addEventListener("change", () => loadStats().catch((e) => toast(e.message, "error")));

/* ---------- 预算 ---------- */
async function loadBudgets() {
    const month = $("#budgetMonth").value;
    const [budgetData, statsData] = await Promise.all([
        api("/api/budgets?month=" + month),
        api("/api/stats?month=" + month)
    ]);
    const spentMap = {};
    (statsData.budgetUsage || []).forEach((u) => { spentMap[u.category] = u.spent; });

    $("#budgetList").innerHTML = budgetData.list.map((b) => {
        const spent = spentMap[b.category] || 0;
        const pct = b.limit > 0 ? Math.min(spent / b.limit * 100, 100) : 0;
        const over = b.limit > 0 && spent > b.limit;
        return `<div class="budget-item">
            <span class="budget-name">${b.icon} ${b.category}</span>
            <input type="number" class="input budget-input" min="0" step="1"
                   data-cat="${b.category}" value="${b.limit > 0 ? b.limit : ""}" placeholder="未设置">
            <div class="budget-bar-wrap"><div class="budget-bar ${over ? "over" : ""}" style="width:${pct}%"></div></div>
            <span class="budget-pct ${over ? "over" : ""}">
                ${b.limit > 0 ? `已用 ¥${spent.toFixed(0)} / ${over ? "超支 " : ""}${(spent / b.limit * 100).toFixed(0)}%` : "已用 ¥" + spent.toFixed(0)}
            </span>
        </div>`;
    }).join("");
}

$("#btnCopyBudgets").addEventListener("click", async () => {
    const currentMonth = $("#budgetMonth").value;
    const [y, m] = currentMonth.split("-").map(Number);
    let lastM = m - 1, lastY = y;
    if (lastM === 0) { lastM = 12; lastY = y - 1; }
    const lastMonth = `${lastY}-${String(lastM).padStart(2, "0")}`;
    try {
        const data = await api("/api/budgets?month=" + lastMonth);
        const budgets = (data.list || []).filter(b => b.limit > 0);
        if (budgets.length === 0) {
            toast("上月没有设置预算", "error");
            return;
        }
        for (const b of budgets) {
            await api("/api/budgets", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ month: currentMonth, category: b.category, limit: b.limit })
            });
        }
        toast(`已复制上月 ${budgets.length} 项预算！`, "success");
        loadBudgets();
    } catch (err) {
        toast(err.message, "error");
    }
});

$("#btnSaveBudgets").addEventListener("click", async () => {
    const month = $("#budgetMonth").value;
    const inputs = $$("#budgetList .budget-input");
    try {
        for (const input of inputs) {
            const limit = input.value.trim() === "" ? 0 : Number(input.value);
            await api("/api/budgets", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ month, category: input.dataset.cat, limit })
            });
        }
        toast("预算已保存！", "success");
        loadBudgets();
    } catch (err) {
        toast(err.message, "error");
    }
});

$("#budgetMonth").addEventListener("change", () => loadBudgets().catch((e) => toast(e.message, "error")));

/* ---------- AI 分析 ---------- */
$("#btnAnalyze").addEventListener("click", async () => {
    const btn = $("#btnAnalyze");
    const status = $("#aiStatus");
    const month = $("#aiMonth").value;
    btn.disabled = true;
    status.className = "ai-status loading";
    status.textContent = "🤖 AI 正在分析 " + month + " 的消费数据，请稍候（约 10~30 秒）...";
    try {
        const data = await api("/api/ai/analyze?month=" + month, { method: "POST" });
        status.className = "ai-status";
        status.textContent = "";
        $("#aiReport").textContent = data.report;
    } catch (err) {
        status.className = "ai-status error";
        status.textContent = "❌ " + err.message;
    } finally {
        btn.disabled = false;
    }
});

/* ---------- 工具 ---------- */
function escapeHtml(s) {
    const div = document.createElement("div");
    div.textContent = s;
    return div.innerHTML;
}

/* ---------- 初始化 ---------- */
async function initAppData() {
    try {
        await loadCategories();
        await loadOverview();
        await updateNoteHints();
    } catch (err) {
        toast("加载数据失败：" + err.message, "error");
    }
}

(async function init() {
    initTheme();
    $("#date").value = new Date().toISOString().slice(0, 10);
    fillAllMonthSelects();
    if (await checkAuth()) {
        await initAppData();
    }
})();
