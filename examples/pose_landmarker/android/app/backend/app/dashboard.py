from pathlib import Path

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse, HTMLResponse, RedirectResponse, Response

from app.config import get_settings

router = APIRouter(tags=["dashboard"])

ANDROID_APP_DIR = Path(__file__).resolve().parents[2]
APK_CANDIDATES = (
    ANDROID_APP_DIR / "build" / "outputs" / "apk" / "debug" / "app-debug.apk",
    ANDROID_APP_DIR / "build" / "intermediates" / "apk" / "debug" / "app-debug.apk",
)


def find_debug_apk() -> Path | None:
    return next((path for path in APK_CANDIDATES if path.exists()), None)


DASHBOARD_HTML = """
<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>HeadUp Data Dashboard</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: #071426;
      --panel: rgba(20, 35, 66, 0.82);
      --panel-strong: rgba(28, 45, 84, 0.95);
      --line: rgba(94, 214, 255, 0.22);
      --text: #f4f8ff;
      --muted: #9fb1cf;
      --cyan: #38cfff;
      --green: #7fe39a;
      --yellow: #ffd166;
      --coral: #ff6b6b;
      --purple: #a989ff;
    }

    * { box-sizing: border-box; }

    body {
      margin: 0;
      min-height: 100vh;
      font-family: Inter, "Noto Sans TC", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background:
        radial-gradient(circle at 22% 14%, rgba(56, 207, 255, 0.16), transparent 28rem),
        radial-gradient(circle at 82% 5%, rgba(169, 137, 255, 0.13), transparent 24rem),
        linear-gradient(180deg, #071426 0%, #0a1024 100%);
      color: var(--text);
    }

    button, input {
      font: inherit;
    }

    .app {
      width: min(1180px, calc(100% - 32px));
      margin: 0 auto;
      padding: 32px 0 40px;
    }

    .topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 22px;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .logo {
      width: 52px;
      height: 52px;
      border-radius: 15px;
      display: grid;
      place-items: center;
      background: linear-gradient(180deg, #5de1ff, #1399d8);
      box-shadow: 0 0 28px rgba(56, 207, 255, 0.38);
      font-size: 34px;
      font-weight: 800;
    }

    h1 {
      margin: 0;
      font-size: clamp(28px, 4vw, 42px);
      line-height: 1;
      letter-spacing: 0;
      color: var(--cyan);
    }

    .subtitle {
      margin: 7px 0 0;
      color: var(--muted);
      font-size: 15px;
    }

    .actions {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }

    .button {
      border: 1px solid var(--line);
      color: var(--text);
      background: rgba(23, 39, 74, 0.78);
      border-radius: 12px;
      padding: 11px 14px;
      cursor: pointer;
      transition: transform 160ms ease, border-color 160ms ease, background 160ms ease;
    }

    .button:hover {
      transform: translateY(-1px);
      border-color: rgba(56, 207, 255, 0.58);
      background: rgba(34, 54, 98, 0.9);
    }

    .button.primary {
      border: 0;
      color: #06101f;
      font-weight: 800;
      background: linear-gradient(180deg, #62dfff, #23bde9);
      box-shadow: 0 0 28px rgba(56, 207, 255, 0.24);
    }

    .status {
      min-height: 22px;
      color: var(--muted);
      margin: 8px 0 16px;
    }

    .login {
      max-width: 430px;
      margin: 70px auto 0;
      padding: 26px;
      border: 1px solid var(--line);
      border-radius: 22px;
      background: var(--panel);
      box-shadow: 0 24px 80px rgba(0, 0, 0, 0.28);
    }

    .login h2 {
      margin: 0 0 8px;
      font-size: 26px;
    }

    .field {
      display: grid;
      gap: 8px;
      margin-top: 16px;
    }

    label {
      color: var(--muted);
      font-size: 14px;
    }

    input {
      width: 100%;
      border: 1px solid rgba(148, 170, 216, 0.2);
      border-radius: 12px;
      color: var(--text);
      background: rgba(5, 14, 31, 0.76);
      padding: 13px 14px;
      outline: none;
    }

    input:focus {
      border-color: var(--cyan);
      box-shadow: 0 0 0 3px rgba(56, 207, 255, 0.12);
    }

    .grid {
      display: grid;
      gap: 16px;
    }

    .kpis {
      grid-template-columns: repeat(5, minmax(0, 1fr));
      margin-bottom: 16px;
    }

    .charts {
      grid-template-columns: 0.84fr 1.16fr;
      margin-bottom: 16px;
    }

    .tables {
      grid-template-columns: 1fr 1fr;
    }

    .card {
      border: 1px solid var(--line);
      border-radius: 18px;
      background: var(--panel);
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.18);
      overflow: hidden;
    }

    .card.pad {
      padding: 20px;
    }

    .kpi-label {
      color: var(--muted);
      font-size: 13px;
      min-height: 20px;
    }

    .kpi-value {
      margin-top: 8px;
      font-size: clamp(24px, 4vw, 34px);
      font-weight: 850;
      letter-spacing: 0;
    }

    .kpi-note {
      margin-top: 6px;
      color: var(--muted);
      font-size: 12px;
    }

    .card-title {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 14px;
      margin-bottom: 16px;
    }

    .card-title h2 {
      margin: 0;
      font-size: 19px;
    }

    .card-title span {
      color: var(--muted);
      font-size: 13px;
    }

    canvas {
      width: 100%;
      height: 280px;
      display: block;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }

    th, td {
      padding: 12px 14px;
      text-align: left;
      border-bottom: 1px solid rgba(148, 170, 216, 0.12);
      vertical-align: top;
    }

    th {
      color: var(--muted);
      font-weight: 650;
      background: rgba(7, 17, 38, 0.46);
    }

    tbody tr:hover {
      background: rgba(56, 207, 255, 0.05);
    }

    .badge {
      display: inline-flex;
      align-items: center;
      border-radius: 999px;
      padding: 4px 9px;
      color: #071426;
      background: var(--cyan);
      font-weight: 800;
      font-size: 12px;
    }

    .badge.guest { background: var(--purple); color: #fff; }
    .badge.admin { background: var(--yellow); }

    .hidden { display: none; }

    @media (max-width: 900px) {
      .topbar { align-items: flex-start; flex-direction: column; }
      .actions { justify-content: flex-start; }
      .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .charts, .tables { grid-template-columns: 1fr; }
      canvas { height: 250px; }
    }

    @media (max-width: 540px) {
      .app { width: min(100% - 20px, 1180px); padding-top: 18px; }
      .kpis { grid-template-columns: 1fr; }
      .login { margin-top: 36px; padding: 20px; }
      th, td { padding: 10px; }
    }
  </style>
</head>
<body>
  <main class="app">
    <section id="loginPanel" class="login">
      <div class="brand">
        <div class="logo">↑</div>
        <div>
          <h1>HeadUp</h1>
          <p class="subtitle">後端資料圖表中心</p>
        </div>
      </div>
      <h2 style="margin-top:28px;">Admin 登入</h2>
      <p class="subtitle">登入後可查看所有註冊用戶、訪客與同步報表。</p>
      <form id="loginForm">
        <div class="field">
          <label for="email">Email</label>
          <input id="email" autocomplete="username" value="admin@headup.local" />
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input id="password" type="password" autocomplete="current-password" />
        </div>
        <button class="button primary" style="width:100%; margin-top:20px;" type="submit">登入 Dashboard</button>
      </form>
      <div id="loginStatus" class="status"></div>
    </section>

    <section id="dashboardPanel" class="hidden">
      <header class="topbar">
        <div class="brand">
          <div class="logo">↑</div>
          <div>
            <h1>HeadUp Data Dashboard</h1>
            <p class="subtitle">即時查看所有用戶與訪客的去識別化姿勢摘要。</p>
          </div>
        </div>
        <div class="actions">
          <button id="refreshButton" class="button primary" type="button">重新整理</button>
          <button id="downloadButton" class="button" type="button">下載 APK</button>
          <button id="docsButton" class="button" type="button">API 文件</button>
          <button id="logoutButton" class="button" type="button">登出</button>
        </div>
      </header>

      <div id="dashboardStatus" class="status"></div>

      <section class="grid kpis">
        <article class="card pad">
          <div class="kpi-label">總用戶</div>
          <div id="totalUsers" class="kpi-value">0</div>
          <div class="kpi-note">註冊 + 訪客</div>
        </article>
        <article class="card pad">
          <div class="kpi-label">訪客</div>
          <div id="guestUsers" class="kpi-value">0</div>
          <div class="kpi-note">匿名裝置帳號</div>
        </article>
        <article class="card pad">
          <div class="kpi-label">報表筆數</div>
          <div id="totalReports" class="kpi-value">0</div>
          <div class="kpi-note">最新 1000 筆</div>
        </article>
        <article class="card pad">
          <div class="kpi-label">低頭事件</div>
          <div id="totalSlouch" class="kpi-value">0</div>
          <div class="kpi-note">同步報表累計</div>
        </article>
        <article class="card pad">
          <div class="kpi-label">AI 攔截率</div>
          <div id="avgIntercept" class="kpi-value">0%</div>
          <div class="kpi-note">平均值</div>
        </article>
      </section>

      <section class="grid charts">
        <article class="card pad">
          <div class="card-title">
            <h2>用戶類型分布</h2>
            <span>users</span>
          </div>
          <canvas id="roleChart" width="520" height="320"></canvas>
        </article>
        <article class="card pad">
          <div class="card-title">
            <h2>七天不良姿勢趨勢</h2>
            <span>slouch count</span>
          </div>
          <canvas id="slouchChart" width="680" height="320"></canvas>
        </article>
      </section>

      <section class="grid tables">
        <article class="card">
          <div class="card-title" style="padding:18px 20px 0;">
            <h2>最新報表</h2>
            <span id="reportTableNote">0 rows</span>
          </div>
          <div style="overflow:auto;">
            <table>
              <thead>
                <tr>
                  <th>日期</th>
                  <th>使用者</th>
                  <th>低頭</th>
                  <th>攔截率</th>
                  <th>EXP</th>
                </tr>
              </thead>
              <tbody id="reportsBody"></tbody>
            </table>
          </div>
        </article>

        <article class="card">
          <div class="card-title" style="padding:18px 20px 0;">
            <h2>用戶清單</h2>
            <span id="userTableNote">0 rows</span>
          </div>
          <div style="overflow:auto;">
            <table>
              <thead>
                <tr>
                  <th>Email / 裝置</th>
                  <th>角色</th>
                  <th>方案</th>
                </tr>
              </thead>
              <tbody id="usersBody"></tbody>
            </table>
          </div>
        </article>
      </section>
    </section>
  </main>

  <script>
    const tokenKey = "headup_admin_token";
    const loginPanel = document.getElementById("loginPanel");
    const dashboardPanel = document.getElementById("dashboardPanel");
    const loginStatus = document.getElementById("loginStatus");
    const dashboardStatus = document.getElementById("dashboardStatus");

    document.getElementById("loginForm").addEventListener("submit", async (event) => {
      event.preventDefault();
      loginStatus.textContent = "登入中...";
      const email = document.getElementById("email").value.trim();
      const password = document.getElementById("password").value;
      try {
        const body = new URLSearchParams({ username: email, password });
        const response = await fetch("/api/v1/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body,
        });
        if (!response.ok) throw new Error(`登入失敗 HTTP ${response.status}`);
        const token = await response.json();
        localStorage.setItem(tokenKey, token.access_token);
        loginStatus.textContent = "";
        showDashboard();
        await loadDashboard();
      } catch (error) {
        loginStatus.textContent = error.message || "登入失敗";
      }
    });

    document.getElementById("refreshButton").addEventListener("click", loadDashboard);
    document.getElementById("logoutButton").addEventListener("click", () => {
      localStorage.removeItem(tokenKey);
      loginPanel.classList.remove("hidden");
      dashboardPanel.classList.add("hidden");
    });
    document.getElementById("docsButton").addEventListener("click", () => {
      window.location.href = "/docs";
    });
    document.getElementById("downloadButton").addEventListener("click", () => {
      window.location.href = "/download";
    });

    function showDashboard() {
      loginPanel.classList.add("hidden");
      dashboardPanel.classList.remove("hidden");
    }

    async function loadDashboard() {
      dashboardStatus.textContent = "載入資料中...";
      try {
        const [users, reports] = await Promise.all([
          apiGet("/api/v1/users"),
          apiGet("/api/v1/reports?limit=1000"),
        ]);
        renderDashboard(users, reports);
        dashboardStatus.textContent = `更新完成：${formatDateTime(new Date())}`;
      } catch (error) {
        dashboardStatus.textContent = error.message || "無法載入資料";
        if (String(error.message || "").includes("401") || String(error.message || "").includes("403")) {
          localStorage.removeItem(tokenKey);
          loginPanel.classList.remove("hidden");
          dashboardPanel.classList.add("hidden");
          loginStatus.textContent = "請用 admin 帳號重新登入。";
        }
      }
    }

    async function apiGet(path) {
      const token = localStorage.getItem(tokenKey);
      const response = await fetch(path, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) throw new Error(`API 錯誤 HTTP ${response.status}`);
      return response.json();
    }

    function renderDashboard(users, reports) {
      const guestCount = users.filter((user) => user.role === "guest").length;
      const totalSlouch = reports.reduce((sum, report) => sum + Number(report.slouch_count || 0), 0);
      const avgIntercept = reports.length
        ? reports.reduce((sum, report) => sum + Number(report.ai_intercept_rate || 0), 0) / reports.length
        : 0;

      setText("totalUsers", users.length);
      setText("guestUsers", guestCount);
      setText("totalReports", reports.length);
      setText("totalSlouch", totalSlouch);
      setText("avgIntercept", `${Math.round(avgIntercept * 100)}%`);

      const roleCounts = countBy(users, (user) => user.role || "user");
      drawDoughnut(document.getElementById("roleChart"), [
        { label: "正式用戶", value: roleCounts.user || 0, color: "#38cfff" },
        { label: "訪客", value: roleCounts.guest || 0, color: "#a989ff" },
        { label: "Admin", value: roleCounts.admin || 0, color: "#ffd166" },
      ]);
      drawLine(document.getElementById("slouchChart"), buildSevenDaySeries(reports));
      renderReportsTable(reports);
      renderUsersTable(users);
    }

    function renderReportsTable(reports) {
      document.getElementById("reportTableNote").textContent = `${reports.length} rows`;
      const rows = reports.slice(0, 80).map((report) => `
        <tr>
          <td>${escapeHtml(report.record_date)}</td>
          <td>${escapeHtml(report.user_email || report.user_id)}</td>
          <td>${Number(report.slouch_count || 0)}</td>
          <td>${Math.round(Number(report.ai_intercept_rate || 0) * 100)}%</td>
          <td>${Number(report.pet_exp || 0)}</td>
        </tr>
      `).join("");
      document.getElementById("reportsBody").innerHTML = rows || emptyRow(5, "目前還沒有同步報表。");
    }

    function renderUsersTable(users) {
      document.getElementById("userTableNote").textContent = `${users.length} rows`;
      const rows = users.slice(0, 120).map((user) => `
        <tr>
          <td>${escapeHtml(user.email)}</td>
          <td><span class="badge ${escapeHtml(user.role)}">${escapeHtml(user.role)}</span></td>
          <td>${escapeHtml(user.subscription_tier)}</td>
        </tr>
      `).join("");
      document.getElementById("usersBody").innerHTML = rows || emptyRow(3, "目前還沒有用戶。");
    }

    function drawDoughnut(canvas, items) {
      const context = setupCanvas(canvas);
      const total = items.reduce((sum, item) => sum + item.value, 0);
      const cx = canvas.width / 2;
      const cy = canvas.height / 2 - 8;
      const radius = Math.min(canvas.width, canvas.height) * 0.3;
      context.clearRect(0, 0, canvas.width, canvas.height);

      if (!total) {
        drawEmpty(context, canvas, "尚無用戶資料");
        return;
      }

      let start = -Math.PI / 2;
      items.forEach((item) => {
        if (!item.value) return;
        const angle = (item.value / total) * Math.PI * 2;
        context.beginPath();
        context.moveTo(cx, cy);
        context.arc(cx, cy, radius, start, start + angle);
        context.closePath();
        context.fillStyle = item.color;
        context.fill();
        start += angle;
      });

      context.globalCompositeOperation = "destination-out";
      context.beginPath();
      context.arc(cx, cy, radius * 0.58, 0, Math.PI * 2);
      context.fill();
      context.globalCompositeOperation = "source-over";

      context.fillStyle = "#f4f8ff";
      context.font = "700 30px system-ui";
      context.textAlign = "center";
      context.fillText(total, cx, cy + 10);
      context.font = "13px system-ui";
      context.fillStyle = "#9fb1cf";
      context.fillText("users", cx, cy + 32);

      let legendX = 28;
      const legendY = canvas.height - 34;
      context.textAlign = "left";
      context.font = "13px system-ui";
      items.forEach((item) => {
        context.fillStyle = item.color;
        context.fillRect(legendX, legendY - 10, 12, 12);
        context.fillStyle = "#c9d5ee";
        context.fillText(`${item.label} ${item.value}`, legendX + 18, legendY);
        legendX += 120;
      });
    }

    function drawLine(canvas, points) {
      const context = setupCanvas(canvas);
      context.clearRect(0, 0, canvas.width, canvas.height);
      if (!points.length) {
        drawEmpty(context, canvas, "尚無報表資料");
        return;
      }

      const pad = { left: 50, right: 22, top: 26, bottom: 46 };
      const width = canvas.width - pad.left - pad.right;
      const height = canvas.height - pad.top - pad.bottom;
      const maxValue = Math.max(...points.map((point) => point.value), 1);

      context.strokeStyle = "rgba(159, 177, 207, 0.22)";
      context.lineWidth = 1;
      for (let i = 0; i <= 4; i += 1) {
        const y = pad.top + (height * i) / 4;
        context.beginPath();
        context.moveTo(pad.left, y);
        context.lineTo(canvas.width - pad.right, y);
        context.stroke();
      }

      context.beginPath();
      points.forEach((point, index) => {
        const x = pad.left + (width * index) / Math.max(points.length - 1, 1);
        const y = pad.top + height - (point.value / maxValue) * height;
        if (index === 0) context.moveTo(x, y);
        else context.lineTo(x, y);
      });
      context.strokeStyle = "#38cfff";
      context.lineWidth = 4;
      context.lineCap = "round";
      context.stroke();

      context.fillStyle = "#38cfff";
      points.forEach((point, index) => {
        const x = pad.left + (width * index) / Math.max(points.length - 1, 1);
        const y = pad.top + height - (point.value / maxValue) * height;
        context.beginPath();
        context.arc(x, y, 5, 0, Math.PI * 2);
        context.fill();
      });

      context.fillStyle = "#9fb1cf";
      context.font = "12px system-ui";
      context.textAlign = "center";
      points.forEach((point, index) => {
        const x = pad.left + (width * index) / Math.max(points.length - 1, 1);
        context.fillText(point.label.slice(5), x, canvas.height - 18);
      });

      context.textAlign = "right";
      context.fillText(maxValue, pad.left - 10, pad.top + 4);
      context.fillText("0", pad.left - 10, pad.top + height + 4);
    }

    function setupCanvas(canvas) {
      const rect = canvas.getBoundingClientRect();
      canvas.width = Math.max(1, Math.floor(rect.width));
      canvas.height = Math.max(1, Math.floor(rect.height));
      return canvas.getContext("2d");
    }

    function drawEmpty(context, canvas, message) {
      context.fillStyle = "rgba(159, 177, 207, 0.8)";
      context.font = "16px system-ui";
      context.textAlign = "center";
      context.fillText(message, canvas.width / 2, canvas.height / 2);
    }

    function buildSevenDaySeries(reports) {
      const days = [];
      const today = new Date();
      for (let i = 6; i >= 0; i -= 1) {
        const date = new Date(today);
        date.setDate(today.getDate() - i);
        days.push(localDateKey(date));
      }
      const values = Object.fromEntries(days.map((day) => [day, 0]));
      reports.forEach((report) => {
        if (values[report.record_date] !== undefined) {
          values[report.record_date] += Number(report.slouch_count || 0);
        }
      });
      return days.map((day) => ({ label: day, value: values[day] }));
    }

    function countBy(items, keySelector) {
      return items.reduce((counts, item) => {
        const key = keySelector(item);
        counts[key] = (counts[key] || 0) + 1;
        return counts;
      }, {});
    }

    function setText(id, value) {
      document.getElementById(id).textContent = value;
    }

    function escapeHtml(value) {
      return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
    }

    function emptyRow(columns, message) {
      return `<tr><td colspan="${columns}" style="color:#9fb1cf;">${message}</td></tr>`;
    }

    function localDateKey(date) {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      return `${year}-${month}-${day}`;
    }

    function formatDateTime(date) {
      return new Intl.DateTimeFormat("zh-TW", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      }).format(date);
    }

    if (localStorage.getItem(tokenKey)) {
      showDashboard();
      loadDashboard();
    }
  </script>
</body>
</html>
"""


@router.get("/dashboard", response_class=HTMLResponse, include_in_schema=False)
async def dashboard() -> HTMLResponse:
    return HTMLResponse(DASHBOARD_HTML)


DOWNLOAD_HTML = """
<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Download HeadUp APK</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: #071426;
      --panel: rgba(20, 35, 66, 0.86);
      --line: rgba(94, 214, 255, 0.24);
      --text: #f4f8ff;
      --muted: #9fb1cf;
      --cyan: #38cfff;
    }

    * { box-sizing: border-box; }

    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 24px;
      font-family: Inter, "Noto Sans TC", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background:
        radial-gradient(circle at 26% 16%, rgba(56, 207, 255, 0.18), transparent 28rem),
        linear-gradient(180deg, #071426 0%, #0a1024 100%);
      color: var(--text);
    }

    .card {
      width: min(680px, 100%);
      border: 1px solid var(--line);
      border-radius: 24px;
      background: var(--panel);
      box-shadow: 0 24px 80px rgba(0, 0, 0, 0.32);
      padding: clamp(24px, 6vw, 42px);
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 26px;
    }

    .logo {
      width: 64px;
      height: 64px;
      display: grid;
      place-items: center;
      border-radius: 18px;
      background: linear-gradient(180deg, #5de1ff, #1399d8);
      box-shadow: 0 0 30px rgba(56, 207, 255, 0.34);
      font-size: 42px;
      font-weight: 900;
    }

    h1 {
      margin: 0;
      color: var(--cyan);
      font-size: clamp(34px, 8vw, 54px);
      line-height: 1;
      letter-spacing: 0;
    }

    p {
      margin: 10px 0 0;
      color: var(--muted);
      line-height: 1.7;
      font-size: 16px;
    }

    .download {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-height: 54px;
      margin-top: 28px;
      padding: 0 24px;
      border-radius: 14px;
      color: #06101f;
      background: linear-gradient(180deg, #62dfff, #23bde9);
      font-weight: 900;
      text-decoration: none;
      box-shadow: 0 0 28px rgba(56, 207, 255, 0.24);
    }

    .note {
      margin-top: 22px;
      padding: 16px;
      border: 1px solid rgba(148, 170, 216, 0.16);
      border-radius: 16px;
      background: rgba(5, 14, 31, 0.52);
    }
  </style>
</head>
<body>
  <main class="card">
    <div class="brand">
      <div class="logo">↑</div>
      <div>
        <h1>HeadUp</h1>
        <p>Android Demo APK 下載</p>
      </div>
    </div>
    <p>點擊下方按鈕下載目前伺服器上的 HeadUp demo APK。下載後 Android 會要求允許「安裝未知來源應用程式」。</p>
    <a class="download" href="/downloads/headup-debug.apk">下載 HeadUp APK</a>
    <div class="note">
      <p>這是 debug demo APK，適合展示與測試。正式大量發佈前，請改用簽署過的 release APK 或 Google Play / AAB 發佈流程。</p>
    </div>
  </main>
</body>
</html>
"""


@router.get("/download", response_class=HTMLResponse, include_in_schema=False)
async def download_page() -> HTMLResponse:
    return HTMLResponse(DOWNLOAD_HTML)


@router.get("/downloads/headup-debug.apk", include_in_schema=False)
async def download_debug_apk() -> Response:
    settings = get_settings()
    if settings.apk_download_url:
        return RedirectResponse(settings.apk_download_url)

    apk_path = find_debug_apk()
    if apk_path is None:
        raise HTTPException(
            status_code=404,
            detail="HeadUp debug APK was not found. Run :app:assembleDebug before downloading.",
        )
    return FileResponse(
        apk_path,
        media_type="application/vnd.android.package-archive",
        filename="HeadUp-demo-debug.apk",
    )
