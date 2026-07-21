# Toyoko Inn Room Alert

定期查詢東橫 INN 指定飯店在指定日期的空房與最低價格，並在有空房或價格下降時，透過 Discord Webhook 發送通知。

## 功能

- 預設每 1 分鐘查詢一次東橫 INN API。
- 第一次成功查詢時，通知所有目前有空房的飯店。
- 後續查詢只在飯店從無房變為有房，或最低價格下降時通知。
- Discord 通知包含飯店名稱、最低價格及帶入入住／退房日期的訂房連結。
- 查詢失敗時保留前一次狀態，避免因暫時性的 API 錯誤產生錯誤通知。
- Discord 通知每 10 間飯店分成一批；只有確認發送成功後才更新該批價格，失敗項目會於下次輪詢重試。
- 啟動時從東橫 INN 官方飯店一覽頁取得飯店名稱與代碼，只查詢 `application.yml` 指定的飯店。
- 若日期、批次大小或任一飯店名稱不正確，會一次列出相關錯誤並停止應用程式，不會執行部分查詢。

監控清單與其他應用設定集中定義於 `src/main/resources/application.yml`。

## 執行需求

- Java 25
- 可連線至東橫 INN 與 Discord 的網路環境
- 一個有權建立 Webhook 的 Discord 伺服器與文字頻道

專案已包含 Maven Wrapper，不需另外安裝 Maven。

## 應用程式設定

以 UTF-8 編輯 `src/main/resources/application.yml`，逐行填入要監控的完整飯店名稱：

```yaml
toyoko-inn:
  hotel-names:
    - 東横INN新横浜駅前本館
    - 東横INN横浜桜木町
  checkin-date: 2027-04-01
  checkout-date: 2027-04-22
  number-of-people: 1
  number-of-room: 1
  smoking-type: noSmoking
  poll-interval: 1m
  availability-batch-size: 30
```

飯店名稱採精確比對，包含空白、全形／半形及 `INN` 大小寫都必須與東橫 INN 官方飯店一覽頁相同。
`availability-batch-size` 控制每次送往東橫 INN 空房 API 的飯店數量，預設為 30，且必須大於 0。
`number-of-people` 是每間房入住人數，`number-of-room` 是房間數，兩者都必須大於 0。
`smoking-type` 只能是 `all`（不限）、`smoking`（吸菸房）或 `noSmoking`（禁菸房）。這三項設定也會套用至 Discord 通知中的訂房連結。

飯店、日期、入住條件與輪詢間隔皆可直接在 `application.yml` 設定。請在每次執行前確認入住與退房日期；設定檔中的日期可能已經過期，不建議未確認就直接使用。入住日不得早於程式執行當日，退房日必須晚於入住日。

| 環境變數 | 必要性 | 格式／範例 | 說明 |
| --- | --- | --- | --- |
| `DISCORD_WEBHOOK_URL` | 通知必填 | `https://discord.com/api/webhooks/...` | Discord Incoming Webhook URL；未設定時程式仍會查詢，但會略過通知。 |

> [!IMPORTANT]
> `DISCORD_WEBHOOK_URL` 等同可向指定頻道發訊息的秘密權杖。請勿將實際網址寫入 `application.yml`、提交到 Git、貼在 issue，或輸出到公開 log。若曾外洩，請立即在 Discord 刪除該 Webhook 並建立新的 Webhook。

### Windows PowerShell

以下設定只套用於目前的 PowerShell 工作階段：

```powershell
$env:DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/你的_WEBHOOK_ID/你的_WEBHOOK_TOKEN"
```

### macOS／Linux

```bash
export DISCORD_WEBHOOK_URL='https://discord.com/api/webhooks/你的_WEBHOOK_ID/你的_WEBHOOK_TOKEN'
```

## 如何取得 Discord Webhook URL

1. 在 Discord 開啟要接收通知的伺服器。
2. 點選伺服器名稱，進入「伺服器設定」（Server Settings）。
3. 在左側選擇「整合」（Integrations），再進入「Webhooks」。
4. 選擇「建立 Webhook」（Create Webhook）或「新增 Webhook」（New Webhook）。
5. 設定 Webhook 名稱，並選擇要接收空房通知的文字頻道。
6. 點選「複製 Webhook URL」（Copy Webhook URL）。
7. 將複製的 URL 設為 `DISCORD_WEBHOOK_URL`，不要貼入原始碼。

建立或管理 Webhook 需要伺服器中的「管理 Webhooks」權限。介面若有調整，請參考 [Discord 官方 Webhook 說明](https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks)；Webhook 的技術細節可參考 [Discord Developer Documentation](https://docs.discord.com/developers/resources/webhook)。

## 啟動應用程式

先確認 `application.yml` 中的飯店與日期，並視需要設定 `DISCORD_WEBHOOK_URL`，再於專案根目錄執行。

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS／Linux：

```bash
./mvnw spring-boot:run
```

本程式是非 Web 排程應用程式，不會開啟 HTTP 連接埠。啟動後排程會自動執行，不需要另外呼叫 endpoint。第一次成功查詢若已有空房，會立即發送通知；這可用來確認 Webhook 設定是否正確。

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
java -jar target/toyoko-inn-room-alert-0.0.1-SNAPSHOT.jar
```

## 常見問題

### 有查到空房但 Discord 沒收到通知

- 確認 `DISCORD_WEBHOOK_URL` 已設定在啟動應用程式的同一個終端機或服務環境。
- 檢查 Webhook 是否仍存在，以及設定的頻道是否正確。
- 確認執行環境可連線至 `discord.com`。
- 查看 log 是否出現「尚未設定 Discord webhook URL」或 Discord HTTP 錯誤。
- 發送失敗的飯店不會更新最新價格，程式會在下次輪詢再次嘗試通知。

### 應用程式啟動失敗

- 確認使用 Java 25：`java -version`。
- 確認日期使用 `yyyy-MM-dd` 格式。
- 確認入住日未早於執行當日。
- 確認退房日期晚於入住日期。
- 確認 `availability-batch-size` 大於 0。
- 確認 `number-of-people` 與 `number-of-room` 都大於 0。
- 確認 `smoking-type` 是 `all`、`smoking` 或 `noSmoking`。
- 確認每個飯店名稱與東橫 INN 官方飯店一覽頁完全相同；程式會在 log 一次列出所有無法識別的名稱。
- 確認執行環境能連線至東橫 INN 飯店一覽頁；無法取得目錄時程式會停止。

### 修改設定後沒有生效

`DISCORD_WEBHOOK_URL` 與設定檔會在應用程式啟動時讀取。使用 `spring-boot:run` 時，修改後請重新啟動；若執行的是已封裝 JAR，修改 `src/main/resources/application.yml` 後必須重新建置 JAR。

## 注意事項

- 本工具依賴東橫 INN 目前的非公開網頁 API；若對方調整 API 格式或存取規則，查詢可能失敗。
- 通知中的價格不硬編碼幣別，實際幣別與最終金額請以訂房頁為準。
- 請合理設定輪詢間隔，避免對外部服務造成不必要的負載。
- 請自行確認使用方式符合東橫 INN 與 Discord 的服務條款。
