# Toyoko Inn Room Alert

定期查詢東橫 INN 指定飯店在指定日期的空房與最低價格，並在有空房或價格下降時，透過 Discord Webhook 或 Email 發送通知。

## 功能

- 預設每 1 分鐘查詢一次東橫 INN API。
- 第一次成功查詢時，通知所有目前有空房的飯店。
- 後續查詢只在飯店從無房變為有房，或最低價格下降時通知。
- Discord 通知包含飯店名稱、最低價格及帶入入住／退房日期的訂房連結。
- Email 通知將同一輪的所有飯店合併為一封 HTML 信件，另附純文字版本；內容包含查詢條件、通知原因（啟動後首次查詢、新釋出空房或價格下降）、最低價格、降價前價格及訂房按鈕。
- 查詢失敗時保留前一次狀態，避免因暫時性的 API 錯誤產生錯誤通知。
- Discord 通知每 10 間飯店分成一批；只有確認發送成功後才更新該批價格，失敗項目會於下次輪詢重試。
- Discord 與 Email 各自記錄已通知的價格；其中一個管道發送失敗時，只有該管道會在下次輪詢重試，另一個管道不會收到重複通知。
- 啟動時從東橫 INN 官方飯店一覽頁取得飯店名稱與代碼，只查詢 `.env` 指定的飯店。
- 若日期、批次大小或任一飯店名稱不正確，會一次列出相關錯誤並停止應用程式，不會執行部分查詢。

監控清單、日期、入住條件、輪詢間隔、Discord Webhook 與 Email 設定均由環境變數提供。使用 Docker Compose 時，設定集中存放於不納入版本控制的 `.env`。

## 執行需求

- Java 25
- 可連線至東橫 INN 的網路環境
- 使用 Discord 通知時：一個有權建立 Webhook 的 Discord 伺服器與文字頻道
- 使用 Email 通知時：一個支援 STARTTLS 的 SMTP 帳號，例如 Gmail

專案已包含 Maven Wrapper，不需另外安裝 Maven。

## 應用程式設定

先將範例檔複製為 `.env`，再以 UTF-8 編輯實際設定：

```powershell
Copy-Item env.example .env
```

`.env` 已加入 `.gitignore`；實際日期、監控清單及 Webhook 不會被提交。`env.example` 僅提供格式與安全範例，不應填入真正的秘密資料。

| 環境變數 | 必要性／預設值 | 格式／範例 | 說明 |
| --- | --- | --- | --- |
| `TOYOKO_INN_CHECKIN_DATE` | 必填 | `2099-01-01` | 入住日期，格式為 `yyyy-MM-dd`，且不得早於執行當日。 |
| `TOYOKO_INN_CHECKOUT_DATE` | 必填 | `2099-01-02` | 退房日期，必須晚於入住日期。 |
| `TOYOKO_INN_HOTEL_NAMES` | 必填 | `飯店A,飯店B` | 以半形逗號分隔的完整飯店名稱。 |
| `TOYOKO_INN_POLL_INTERVAL` | `1m` | `30s`、`1m`、`1h` | 查詢間隔，使用 Spring Duration 格式。 |
| `TOYOKO_INN_NUMBER_OF_PEOPLE` | `1` | `2` | 每間房入住人數，必須大於 0。 |
| `TOYOKO_INN_NUMBER_OF_ROOM` | `1` | `1` | 要預訂的房間數量，必須大於 0。 |
| `TOYOKO_INN_SMOKING_TYPE` | `noSmoking` | `all` | 只能是 `all`、`smoking` 或 `noSmoking`。 |
| `TOYOKO_INN_AVAILABILITY_BATCH_SIZE` | `30` | `30` | 單次空房 API 請求包含的飯店數量，必須大於 0。 |
| `TOYOKO_INN_DISCORD_WEBHOOK_URL` | 空值 | `https://discord.com/api/webhooks/...` | Discord Incoming Webhook URL；未設定或留空時停用 Discord 通知。 |
| `TOYOKO_INN_EMAIL_ENABLED` | `false` | `true` | 是否啟用 Email 通知。 |
| `TOYOKO_INN_EMAIL_TO` | 啟用 Email 時必填 | `a@example.com,b@example.com` | 收件地址，以半形逗號分隔。 |
| `TOYOKO_INN_EMAIL_SMTP_HOST` | `smtp.gmail.com` | `smtp.example.com` | SMTP 伺服器主機。 |
| `TOYOKO_INN_EMAIL_SMTP_PORT` | `587` | `587` | SMTP 連接埠；連線一律使用 STARTTLS，因此不支援只接受 SSL/TLS 的 465。 |
| `TOYOKO_INN_EMAIL_USERNAME` | 啟用 Email 時必填 | `sender@example.com` | SMTP 登入帳號，同時作為寄件地址，必須是有效的電子郵件地址。 |
| `TOYOKO_INN_EMAIL_PASSWORD` | 啟用 Email 時必填 | — | SMTP 登入密碼；Gmail 須使用應用程式密碼。 |

Discord 與 Email 可以同時啟用，也可以只啟用其中一個；兩者都未設定時仍會查詢，但不會發送任何通知。

必填項目未設定時，應用程式會在啟動時列出所有缺漏並停止，不會執行查詢。

飯店名稱採精確比對，包含空白、全形／半形及 `INN` 大小寫都必須與東橫 INN 官方飯店一覽頁相同。每次啟動或部署前都應重新確認日期，避免沿用已過期的執行期設定。

> [!IMPORTANT]
> `TOYOKO_INN_DISCORD_WEBHOOK_URL` 等同可向指定頻道發訊息的秘密權杖。請勿將實際網址寫入 `application.yml`、`env.example`、提交到 Git、貼在 issue，或輸出到公開 log。若曾外洩，請立即在 Discord 刪除該 Webhook 並建立新的 Webhook。`TOYOKO_INN_EMAIL_PASSWORD` 同樣是秘密資料，處理方式相同；若曾外洩，請撤銷該密碼並重新產生。

## 如何取得 Discord Webhook URL

1. 在 Discord 開啟要接收通知的伺服器。
2. 點選伺服器名稱，進入「伺服器設定」（Server Settings）。
3. 在左側選擇「整合」（Integrations），再進入「Webhooks」。
4. 選擇「建立 Webhook」（Create Webhook）或「新增 Webhook」（New Webhook）。
5. 設定 Webhook 名稱，並選擇要接收空房通知的文字頻道。
6. 點選「複製 Webhook URL」（Copy Webhook URL）。
7. 將複製的 URL 設為 `.env` 中的 `TOYOKO_INN_DISCORD_WEBHOOK_URL`，不要貼入原始碼。

建立或管理 Webhook 需要伺服器中的「管理 Webhooks」權限。介面若有調整，請參考 [Discord 官方 Webhook 說明](https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks)；Webhook 的技術細節可參考 [Discord Developer Documentation](https://docs.discord.com/developers/resources/webhook)。

## 如何取得 Gmail 應用程式密碼

Gmail 不接受以一般登入密碼進行 SMTP 驗證，必須改用「應用程式密碼」。建議另外申請一個專門寄送通知的 Gmail 帳號，避免主要帳號的密碼存放於 `.env`。

1. 登入要作為寄件者的 Google 帳戶，開啟 [Google 帳戶的安全性頁面](https://myaccount.google.com/security)。
2. 在「Google 登入方式」中開啟「兩步驟驗證」；未開啟時無法建立應用程式密碼。
3. 開啟 [應用程式密碼頁面](https://myaccount.google.com/apppasswords)，輸入易於辨識的名稱後選擇「建立」。
4. 複製畫面上顯示的 16 字元密碼；關閉視窗後就無法再次查看。
5. 將該 Gmail 地址設為 `TOYOKO_INN_EMAIL_USERNAME`，將密碼設為 `TOYOKO_INN_EMAIL_PASSWORD`，並把 `TOYOKO_INN_EMAIL_ENABLED` 設為 `true`。

若選單位置有調整，請參考 [Google 官方應用程式密碼說明](https://support.google.com/accounts/answer/185833)。使用其他郵件服務時，請依該服務提供的 SMTP 主機、連接埠與驗證方式設定。

## 使用 Docker Compose 啟動

確認 `.env` 後，在專案根目錄執行：

```powershell
docker compose up -d --build
docker compose logs -f room-alert
```

停止容器：

```powershell
docker compose down
```

本程式是非 Web 排程應用程式，不會開啟 HTTP 連接埠。啟動後排程會自動執行，不需要另外呼叫 endpoint。第一次成功查詢若已有空房，會立即發送通知；這可用來確認 Webhook 與 Email 設定是否正確。

`.env` 只在容器啟動時注入環境變數，不會寫入映像檔。

## 建置與測試

Windows：

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd clean package
```

macOS／Linux：

```bash
./mvnw clean verify
./mvnw clean package
```

封裝完成後，可執行：

```powershell
$env:TOYOKO_INN_CHECKIN_DATE = "2099-01-01"
$env:TOYOKO_INN_CHECKOUT_DATE = "2099-01-02"
$env:TOYOKO_INN_HOTEL_NAMES = "飯店A,飯店B"
# 其他選填變數可視需要設定。
java -jar target/toyoko-inn-room-alert-0.0.1-SNAPSHOT.jar
```

`.env` 由 Docker Compose 自動讀取；直接執行 Maven 或 JAR 時不會自動載入該檔案，必須先在目前的 shell 設定必填環境變數及需要調整的選填變數。

## 常見問題

### 有查到空房但 Discord 沒收到通知

- 確認 `TOYOKO_INN_DISCORD_WEBHOOK_URL` 已設定，且容器已在修改 `.env` 後重新建立。
- 檢查 Webhook 是否仍存在，以及設定的頻道是否正確。
- 確認執行環境可連線至 `discord.com`。
- 查看啟動 log 中「已啟用的通知管道」是否包含 `Discord=true`，以及是否有 Discord HTTP 錯誤。
- 發送失敗的飯店不會更新最新價格，程式會在下次輪詢再次嘗試通知。

### 有查到空房但沒收到 Email

- 確認 `TOYOKO_INN_EMAIL_ENABLED=true`，且啟動 log 中「已啟用的通知管道」包含 `Email=true`。
- 查看 log 是否出現「發送 Email 通知失敗」；驗證失敗通常代表帳號或應用程式密碼錯誤，連線逾時則應確認執行環境可連線至 SMTP 主機的連接埠。
- 檢查收件匣的垃圾郵件與促銷分類。
- 寄送失敗時，本輪 Email 不會更新價格，程式會在下次輪詢重新寄送。

### 應用程式啟動失敗

- 直接執行 JAR 時，確認使用 Java 25：`java -version`。
- 確認 `TOYOKO_INN_CHECKIN_DATE`、`TOYOKO_INN_CHECKOUT_DATE` 與 `TOYOKO_INN_HOTEL_NAMES` 都已設定。
- 確認日期使用 `yyyy-MM-dd` 格式。
- 確認入住日未早於執行當日。
- 確認退房日期晚於入住日期。
- 確認 `TOYOKO_INN_AVAILABILITY_BATCH_SIZE` 大於 0。
- 確認 `TOYOKO_INN_NUMBER_OF_PEOPLE` 與 `TOYOKO_INN_NUMBER_OF_ROOM` 都大於 0。
- 確認 `TOYOKO_INN_SMOKING_TYPE` 是 `all`、`smoking` 或 `noSmoking`。
- 啟用 Email 通知時，確認收件地址、SMTP 帳號與密碼都已設定，且收件地址與帳號都是有效的電子郵件地址。
- 確認每個飯店名稱與東橫 INN 官方飯店一覽頁完全相同；程式會在 log 一次列出所有無法識別的名稱。
- 確認執行環境能連線至東橫 INN 飯店一覽頁；無法取得目錄時程式會停止。

### 修改設定後沒有生效

環境變數只會在應用程式啟動時讀取。修改 `.env` 後，請以 `docker compose up -d --force-recreate` 重新建立容器。

## 注意事項

- 本工具依賴東橫 INN 目前的非公開網頁 API；若對方調整 API 格式或存取規則，查詢可能失敗。
- 通知中的價格不硬編碼幣別，實際幣別與最終金額請以訂房頁為準。
- 請合理設定輪詢間隔，避免對外部服務造成不必要的負載。
- 請自行確認使用方式符合東橫 INN、Discord 及所用郵件服務的服務條款。
