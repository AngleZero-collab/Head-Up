# Head Up 校園姿勢挑戰賽與背景守護成效分析計畫

## 現有架構調查

- Android：Kotlin、XML View Binding、Navigation、CameraX 1.4.2、MediaPipe Tasks Vision 0.10.29。
- 支援版本：`minSdk 24`、`targetSdk 34`、`compileSdk 34`。
- 背景偵測：`HeadUpService` 已是 `foregroundServiceType="camera"` 的前景服務，使用前鏡頭與 `ImageAnalysis`。
- 姿勢規則：沿用 `PostureAnalyzer` 的角度 15/25 度、距離 30/20 公分、肩膀 8/14 度與 landmark 信心 0.20，不修改判斷門檻。
- 提醒：已有持續通知、聲音、震動、紅框與可拖曳寵物 overlay；紅框和寵物 overlay 維持獨立開關。
- 本機資料：Room 2.7.2 + SQLCipher，資料庫版本 2，目前保留約 1 秒一筆的姿勢紀錄與 WorkManager 離線同步。
- 帳號／後端：Firebase 套件未啟用；現有正式程式路徑使用 Retrofit + FastAPI + SQLAlchemy，可用 SQLite 開發、PostgreSQL 正式部署。
- UI：傳統 View/XML，Head Up 深藍、青色、綠黃紅語意色；不導入 Compose。
- 專案未找到 `AGENTS.md`，README 仍是 MediaPipe 範例說明。

## 可行性與 Android 限制

- CameraX + MediaPipe 已能在 Activity 及 Lifecycle 前景服務執行，技術上可行。
- Android 12+ 不允許一般背景情境任意啟動前景服務；使用者必須在 Head Up 可見畫面按下開始。
- Android 13+ 需通知權限；即使使用者拒絕通知權限，前景服務仍受系統規則管理，App 必須明確顯示狀態。
- Android 14+ camera foreground service 需要 `FOREGROUND_SERVICE_CAMERA`，且啟動時必須具有 while-in-use 相機權限。本專案已宣告權限，啟動入口仍限制在 App 畫面。
- 偵測期間 Android 會顯示相機指示；Head Up 不會隱藏該指示。
- 相機被其他 App 佔用時切換為 `CAMERA_UNAVAILABLE`，不計分、不視為姿勢不良。
- 螢幕關閉、權限撤銷或使用者停止時釋放 CameraX；螢幕重新開啟後只恢復先前由使用者啟動的有效 session。
- 不使用開機廣播、排程或 Accessibility Service 啟動相機。

## 預計修改範圍

- Domain：偵測狀態、`ScoringConfig`、10 秒計分窗、PostureScore、排行榜資格、守護成效比較。
- Data：Room 版本 3、session/window/daily/reminder/education/school/enrollment/cache/sync queue。
- Service：OBSERVATION／GUARDING、UNKNOWN、相機錯誤、螢幕狀態、提醒冷卻、FPS 與效能取樣。
- Repository：模式與偏好、聚合查詢、Fake 校園排行榜、教育資料、離線同步。
- UI：偵測控制、守護成效頁、校園挑戰頁、排行榜與教育資料設定。
- Backend：沿用 FastAPI，新增 schools/profile/posture aggregates/leaderboards/insights API、伺服器重算與 idempotency。

## 資料流與狀態轉換

1. 使用者在 Head Up 內選擇日常觀察或背景守護並主動開始。
2. `OFF -> OBSERVATION/GUARDING`，前景服務建立 `MonitoringSession` 並啟動相機。
3. MediaPipe 結果經既有平滑與門檻判斷，再轉為 GREEN/YELLOW/RED/UNKNOWN。
4. `PostureScoringEngine` 使用單調時鐘，將樣本聚合成 10 秒 `PostureWindow`。
5. Window 寫入 Room 並更新 `DailyPostureAggregate`；不保存影像或 landmarks。
6. GUARDING 才能在既有 3 秒門檻後提醒；提醒與回綠紀錄到 `ReminderEvent`。
7. WorkManager 將未同步 aggregate 以 idempotency key 批次上傳；伺服器驗證並重算正式排名。
8. PAUSED/CAMERA_UNAVAILABLE/PERMISSION_REQUIRED/ERROR 期間輸入 UNKNOWN，不加扣分。

## Room migration

- 版本 2 升至 3，保留 `posture_records` 全部資料。
- 新增九張聚合與功能資料表，不破壞、刪除或重建既有表。
- migration test 會先建立版本 2 結構與舊資料，再執行 migration，確認舊資料仍存在且新表可讀寫。

## 後端與 API 契約

- `GET /api/v1/schools`：國家、階段、地區、關鍵字與分頁查詢。
- `PUT /api/v1/profile/education`：儲存學校／年級與排行榜 opt-in。
- `POST /api/v1/posture-aggregates/batch`：每筆資料帶 `idempotency_key` 的聚合批次同步。
- `GET /api/v1/leaderboards`：entity、scope、period、分頁與自己的鄰近排名。
- `GET /api/v1/insights/comparison`：同一使用者 OBSERVATION／GUARDING 成效。
- 用戶端不送最終名次；正式 PostureScore 與學校中位數由伺服器重算。
- 正式環境必須 TLS；本機開發可使用 LAN HTTP。

## 測試計畫

- JVM：計分、倍率、UNKNOWN、每日上限、PostureScore、除以零、比較公式、排名資格與中位數。
- Instrumentation：Room 2→3 migration、模式偏好、動畫／既有功能回歸。
- Service：通知、日常觀察不提醒、背景守護提醒、停止釋放、權限／相機不可用、螢幕關閉。
- Backend：idempotency、不合理時數、未知版本、隱私欄位、分頁及學校最低人數。
- 完成前執行 unit test、instrumentation、lint、assembleDebug，並在已連接手機安裝驗收。

## 隱私與風險

- 所有姿勢推論在裝置端；不錄影、不截圖、不錄音，不讀取其他 App 畫面或名稱。
- 不上傳原始影像、影片、臉部資料或完整 landmark。
- 公開榜僅顯示隨機／自訂公開暱稱，預設不加入排行榜。
- 學校、年級只用於篩選與統計；未驗證學校不可進正式校榜。
- 上線前仍需法律審查：資料保存期限、家長同意、未成年與各國法規。

## 仍需產品決策

- 正式學校名錄的更新頻率、教育部資料授權與後端排程責任人。
- 未成年家長同意流程、最低年齡、資料保存期限與刪除 SLA。
- 正式後端部署環境、TLS 網域、營運監控與管理員驗證流程。
- V1 預設提醒冷卻時間與預設偵測 FPS 可先採 60 秒／5 FPS，日後由後端設定調整。
