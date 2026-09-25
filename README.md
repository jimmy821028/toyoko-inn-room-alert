# Toyoko Inn Room Alert

定期查詢東橫 INN 指定飯店在指定日期的空房與最低價格，並在有空房或價格下降時，透過 Discord Webhook 發送通知。

## 功能

- 預設每 1 分鐘查詢一次東橫 INN API。
- 第一次成功查詢時，通知所有目前有空房的飯店。
- 後續查詢只在飯店從無房變為有房，或最低價格下降時通知。
- Discord 通知包含飯店名稱、最低價格及帶入入住／退房日期的訂房連結。
- 查詢失敗時保留前一次狀態，避免因暫時性的 API 錯誤產生錯誤通知。
- Discord 通知每 10 間飯店分成一批；只有確認發送成功後才更新該批價格，失敗項目會於下次輪詢重試。
- 啟動時從東橫 INN 官方飯店一覽頁取得飯店名稱與代碼，只查詢 `.env` 指定的飯店。
- 若日期、批次大小或任一飯店名稱不正確，會一次列出相關錯誤並停止應用程式，不會執行部分查詢。

監控清單、日期、入住條件、輪詢間隔與 Discord Webhook 均由環境變數提供。使用 Docker Compose 時，設定集中存放於不納入版本控制的 `.env`。

## 執行需求

- Java 25
- 可連線至東橫 INN 與 Discord 的網路環境
- 一個有權建立 Webhook 的 Discord 伺服器與文字頻道

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
| `TOYOKO_INN_DISCORD_WEBHOOK_URL` | 空值 | `https://discord.com/api/webhooks/...` | Discord Incoming Webhook URL；未設定或留空時仍會查詢，但略過通知。 |

必填項目未設定時，應用程式會在啟動時列出所有缺漏並停止，不會執行查詢。

飯店名稱採精確比對，包含空白、全形／半形及 `INN` 大小寫都必須與東橫 INN 官方飯店一覽頁相同。每次啟動或部署前都應重新確認日期，避免沿用已過期的執行期設定。

> [!IMPORTANT]
> `TOYOKO_INN_DISCORD_WEBHOOK_URL` 等同可向指定頻道發訊息的秘密權杖。請勿將實際網址寫入 `application.yml`、`env.example`、提交到 Git、貼在 issue，或輸出到公開 log。若曾外洩，請立即在 Discord 刪除該 Webhook 並建立新的 Webhook。

## 如何取得 Discord Webhook URL

1. 在 Discord 開啟要接收通知的伺服器。
2. 點選伺服器名稱，進入「伺服器設定」（Server Settings）。
3. 在左側選擇「整合」（Integrations），再進入「Webhooks」。
4. 選擇「建立 Webhook」（Create Webhook）或「新增 Webhook」（New Webhook）。
5. 設定 Webhook 名稱，並選擇要接收空房通知的文字頻道。
6. 點選「複製 Webhook URL」（Copy Webhook URL）。
7. 將複製的 URL 設為 `.env` 中的 `TOYOKO_INN_DISCORD_WEBHOOK_URL`，不要貼入原始碼。

建立或管理 Webhook 需要伺服器中的「管理 Webhooks」權限。介面若有調整，請參考 [Discord 官方 Webhook 說明](https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks)；Webhook 的技術細節可參考 [Discord Developer Documentation](https://docs.discord.com/developers/resources/webhook)。

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

本程式是非 Web 排程應用程式，不會開啟 HTTP 連接埠。啟動後排程會自動執行，不需要另外呼叫 endpoint。第一次成功查詢若已有空房，會立即發送通知；這可用來確認 Webhook 設定是否正確。

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
- 查看 log 是否出現「尚未設定 Discord webhook URL」或 Discord HTTP 錯誤。
- 發送失敗的飯店不會更新最新價格，程式會在下次輪詢再次嘗試通知。

### 應用程式啟動失敗

- 直接執行 JAR 時，確認使用 Java 25：`java -version`。
- 確認 `TOYOKO_INN_CHECKIN_DATE`、`TOYOKO_INN_CHECKOUT_DATE` 與 `TOYOKO_INN_HOTEL_NAMES` 都已設定。
- 確認日期使用 `yyyy-MM-dd` 格式。
- 確認入住日未早於執行當日。
- 確認退房日期晚於入住日期。
- 確認 `TOYOKO_INN_AVAILABILITY_BATCH_SIZE` 大於 0。
- 確認 `TOYOKO_INN_NUMBER_OF_PEOPLE` 與 `TOYOKO_INN_NUMBER_OF_ROOM` 都大於 0。
- 確認 `TOYOKO_INN_SMOKING_TYPE` 是 `all`、`smoking` 或 `noSmoking`。
- 確認每個飯店名稱與東橫 INN 官方飯店一覽頁完全相同；程式會在 log 一次列出所有無法識別的名稱。
- 確認執行環境能連線至東橫 INN 飯店一覽頁；無法取得目錄時程式會停止。

### 修改設定後沒有生效

環境變數只會在應用程式啟動時讀取。修改 `.env` 後，請以 `docker compose up -d --force-recreate` 重新建立容器。

## 注意事項

- 本工具依賴東橫 INN 目前的非公開網頁 API；若對方調整 API 格式或存取規則，查詢可能失敗。
- 通知中的價格不硬編碼幣別，實際幣別與最終金額請以訂房頁為準。
- 請合理設定輪詢間隔，避免對外部服務造成不必要的負載。
- 請自行確認使用方式符合東橫 INN 與 Discord 的服務條款。
