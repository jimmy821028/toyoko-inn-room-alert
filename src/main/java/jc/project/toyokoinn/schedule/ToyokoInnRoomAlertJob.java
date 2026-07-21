package jc.project.toyokoinn.schedule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import jc.project.toyokoinn.config.ToyokoInnProperties;
import jc.project.toyokoinn.model.HotelsAvailabilitiesPrices;
import jc.project.toyokoinn.model.Room;
import jc.project.toyokoinn.service.ToyokoInnHotelCatalog;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ToyokoInnRoomAlertJob {

    private static final String TOYOKO_INN_API_URL =
            "https://www.toyoko-inn.com/api/trpc/hotels.availabilities.prices";
    private static final String BOOKING_URL = "https://www.toyoko-inn.com/search/result/room_plan/";
    private static final ZoneId QUERY_TIME_ZONE = ZoneId.of("Asia/Taipei");
    private static final int DISCORD_EMBED_COLOR_GREEN = 0x00FF00;
    private static final int DISCORD_MAX_EMBEDS_PER_MESSAGE = 10;

    private final RestClient restClient;
    private final ToyokoInnHotelCatalog hotelCatalog;
    private final String discordWebhookUrl;
    private final LocalDate checkinDate;
    private final LocalDate checkoutDate;
    private final int numberOfPeople;
    private final int numberOfRoom;
    private final String smokingType;
    private final int availabilityBatchSize;
    private final Map<String, Integer> previousPrices = new HashMap<>();

    private boolean hasPreviousResult;

    /**
     * 建立東橫 INN 空房輪詢工作，並設定查詢與通知所需的參數。
     *
     * @param restClientBuilder REST 用戶端建構器
     * @param hotelCatalog 已驗證的飯店目錄
     * @param properties 東橫 INN 查詢設定
     */
    public ToyokoInnRoomAlertJob(
            RestClient.Builder restClientBuilder,
            ToyokoInnHotelCatalog hotelCatalog,
            ToyokoInnProperties properties) {
        this.restClient = restClientBuilder.clone()
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .defaultHeader("Accept-Language", "zh-TW,zh;q=0.9,ja;q=0.8,en-US;q=0.7,en;q=0.6")
                .defaultHeader("Referer", "https://www.toyoko-inn.com/ja/search")
                .build();
        this.hotelCatalog = hotelCatalog;
        this.discordWebhookUrl = properties.getDiscord().getWebhookUrl();
        this.checkinDate = properties.getCheckinDate();
        this.checkoutDate = properties.getCheckoutDate();
        this.numberOfPeople = properties.getNumberOfPeople();
        this.numberOfRoom = properties.getNumberOfRoom();
        this.smokingType = properties.getSmokingType();
        this.availabilityBatchSize = properties.getAvailabilityBatchSize();
    }

    /**
     * 定期查詢東橫 INN 空房，並將符合通知條件的房間傳送至 Discord。
     */
    @Scheduled(fixedRateString = "${toyoko-inn.poll-interval:1m}")
    public synchronized void runCheckingToyokoInnRoomJob() {
        if (!hotelCatalog.isReady()) {
            log.debug("飯店名稱尚未完成驗證，略過本次空房查詢");
            return;
        }

        Optional<List<Room>> roomsResult = fetchRooms(hotelCatalog.getResolvedHotels());
        if (roomsResult.isEmpty()) {
            return;
        }

        List<Room> rooms = roomsResult.get();
        List<Room> roomsToNotify = findRoomsToNotify(rooms, previousPrices, hasPreviousResult);
        Set<String> successfullyNotifiedCodes = notifyDiscord(roomsToNotify);
        updatePreviousPrices(rooms, roomsToNotify, successfullyNotifiedCodes, previousPrices);
        hasPreviousResult = true;

        if (roomsToNotify.isEmpty()) {
            log.info("未有符合通知條件的空房");
        } else if (successfullyNotifiedCodes.size() < roomsToNotify.size()) {
            log.warn("本次有 {} 筆通知未成功，保留原價格狀態並於下次輪詢重試",
                    roomsToNotify.size() - successfullyNotifiedCodes.size());
        }
    }

    /**
     * 根據本次與前次價格，篩選首次有空房或價格下降的飯店。
     *
     * @param rooms 本次查詢到的房間資料
     * @param previousPrices 前次依飯店代碼記錄的最低價格
     * @param hasPreviousResult 是否已有成功的歷史查詢結果
     * @return 符合通知條件的房間清單
     */
    static List<Room> findRoomsToNotify(List<Room> rooms, Map<String, Integer> previousPrices,
            boolean hasPreviousResult) {
        if (!hasPreviousResult) {
            return rooms.stream()
                    .filter(room -> room.getLowestPrice() > 0)
                    .toList();
        }

        return rooms.stream()
                .filter(room -> isNotifiablePriceChange(
                        previousPrices.getOrDefault(room.getCode(), 0),
                        room.getLowestPrice()))
                .toList();
    }

    /**
     * 判斷目前價格是否代表新釋出空房或價格下降。
     *
     * @param previousPrice 前次最低價格，零表示先前無空房
     * @param currentPrice 目前最低價格，零表示目前無空房
     * @return 符合通知條件時回傳 {@code true}
     */
    static boolean isNotifiablePriceChange(int previousPrice, int currentPrice) {
        return currentPrice > 0 && (previousPrice == 0 || currentPrice < previousPrice);
    }

    /**
     * 只提交不需通知或已成功通知的價格，讓通知失敗的飯店下次能再次嘗試。
     *
     * @param rooms 本次查詢結果
     * @param roomsToNotify 本次需要通知的飯店
     * @param successfullyNotifiedCodes 已成功通知的飯店代碼
     * @param previousPrices 要更新的歷史價格
     */
    static void updatePreviousPrices(List<Room> rooms, List<Room> roomsToNotify,
            Set<String> successfullyNotifiedCodes, Map<String, Integer> previousPrices) {
        Set<String> notificationRequiredCodes = roomsToNotify.stream()
                .map(Room::getCode)
                .collect(Collectors.toSet());

        rooms.stream()
                .filter(room -> !notificationRequiredCodes.contains(room.getCode())
                        || successfullyNotifiedCodes.contains(room.getCode()))
                .forEach(room -> previousPrices.put(room.getCode(), room.getLowestPrice()));
    }

    /**
     * 呼叫東橫 INN API，取得指定飯店的最低房價與空房資料。
     *
     * @param hotelNamesByCode 已驗證的飯店代碼與名稱
     * @return 查詢成功時包含房間清單；失敗或回應不完整時為空值
     */
    private Optional<List<Room>> fetchRooms(Map<String, String> hotelNamesByCode) {
        List<String> hotelCodes = hotelNamesByCode.keySet().stream().toList();
        List<Room> rooms = new ArrayList<>();

        for (int fromIndex = 0; fromIndex < hotelCodes.size(); fromIndex += availabilityBatchSize) {
            int toIndex = Math.min(fromIndex + availabilityBatchSize, hotelCodes.size());
            Optional<List<Room>> batchResult = fetchRoomBatch(
                    hotelCodes.subList(fromIndex, toIndex), hotelNamesByCode);
            if (batchResult.isEmpty()) {
                return Optional.empty();
            }
            rooms.addAll(batchResult.get());
        }

        log.info("成功取得東橫 INN 空房資料，共 {} 間飯店", rooms.size());
        return Optional.of(rooms);
    }

    private Optional<List<Room>> fetchRoomBatch(List<String> hotelCodes,
            Map<String, String> hotelNamesByCode) {
        try {
            HotelsAvailabilitiesPrices[] responses = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(TOYOKO_INN_API_URL)
                            .queryParam("batch", 1)
                            .queryParam("input", generateInputJson(hotelCodes))
                            .build()
                            .encode()
                            .toUri())
                    .retrieve()
                    .body(HotelsAvailabilitiesPrices[].class);

            if (responses == null || responses.length == 0) {
                log.warn("東橫 INN 空房 API 未回傳資料");
                return Optional.empty();
            }

            HotelsAvailabilitiesPrices response = responses[0];
            if (response == null
                    || response.getResult() == null
                    || response.getResult().getData() == null
                    || response.getResult().getData().getJson() == null
                    || response.getResult().getData().getJson().getPrices() == null) {
                log.warn("東橫 INN 空房 API 回應格式不完整");
                return Optional.empty();
            }

            List<Room> rooms = response.getResult().getData().getJson().getPrices().entrySet().stream()
                    .filter(entry -> hotelNamesByCode.containsKey(entry.getKey()))
                    .map(entry -> {
                        Room room = new Room();
                        room.setCode(entry.getKey());
                        room.setName(hotelNamesByCode.get(entry.getKey()));
                        room.setLowestPrice(entry.getValue().getLowestPrice());
                        return room;
                    })
                    .toList();
            return Optional.of(rooms);
        } catch (Exception e) {
            log.error("取得東橫 INN 資料失敗", e);
            return Optional.empty();
        }
    }

    /**
     * 透過 Discord Webhook 發送空房通知。
     *
     * @param rooms 要通知的房間清單
     */
    private Set<String> notifyDiscord(List<Room> rooms) {
        if (rooms.isEmpty()) {
            return Set.of();
        }
        if (discordWebhookUrl.isBlank()) {
            log.warn("尚未設定 Discord webhook URL，略過 {} 筆空房通知", rooms.size());
            return Set.of();
        }

        Set<String> successfullyNotifiedCodes = new LinkedHashSet<>();
        for (List<Room> batch : createDiscordBatches(rooms)) {
            if (sendDiscordBatch(batch)) {
                batch.stream().map(Room::getCode).forEach(successfullyNotifiedCodes::add);
            }
        }
        return successfullyNotifiedCodes;
    }

    static List<List<Room>> createDiscordBatches(List<Room> rooms) {
        List<List<Room>> batches = new ArrayList<>();
        for (int fromIndex = 0; fromIndex < rooms.size(); fromIndex += DISCORD_MAX_EMBEDS_PER_MESSAGE) {
            int toIndex = Math.min(fromIndex + DISCORD_MAX_EMBEDS_PER_MESSAGE, rooms.size());
            batches.add(List.copyOf(rooms.subList(fromIndex, toIndex)));
        }
        return batches;
    }

    private boolean sendDiscordBatch(List<Room> rooms) {
        log.info("開始通知 Discord，本批空房列表：{}", rooms);
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(UriComponentsBuilder.fromUriString(discordWebhookUrl)
                            .replaceQueryParam("wait", true)
                            .build()
                            .encode()
                            .toUri())
                    .body(Map.of("embeds", createDiscordEmbeds(rooms)))
                    .retrieve()
                    .toBodilessEntity();

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Discord 通知發送成功，共 {} 筆", rooms.size());
                return true;
            } else {
                log.error("Discord 通知發送失敗，HTTP 狀態碼：{}", response.getStatusCode());
            }
        } catch (RestClientResponseException e) {
            log.error("Discord 通知發送失敗，HTTP 狀態碼：{}，回應內容：{}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("發送 Discord 通知時發生例外錯誤", e);
        }
        return false;
    }

    /**
     * 將房間資料轉換成 Discord Embed 訊息內容。
     *
     * @param rooms 要顯示的房間清單
     * @return 可直接放入 Discord Webhook 請求的 Embed 清單
     */
    private List<Map<String, Object>> createDiscordEmbeds(List<Room> rooms) {
        return rooms.stream()
                .map(room -> {
                    Map<String, Object> embed = new LinkedHashMap<>();
                    embed.put("title", "有空房釋出！");
                    embed.put("color", DISCORD_EMBED_COLOR_GREEN);
                    embed.put("fields", List.of(
                            Map.of(
                                    "name", "分店名稱",
                                    "value", room.getName(),
                                    "inline", false),
                            Map.of(
                                    "name", "目前最低價格",
                                    "value", "%,d（幣別以訂房頁為準）".formatted(room.getLowestPrice()),
                                    "inline", false),
                            Map.of(
                                    "name", "訂房連結",
                                    "value", "[立即訂房](" + createBookingUrl(room.getCode()) + ")",
                                    "inline", false)));
                    embed.put("footer", Map.of("text", "東橫 INN 空房通知"));
                    return embed;
                })
                .toList();
    }

    /**
     * 建立包含飯店、入住與退房條件的東橫 INN 訂房網址。
     *
     * @param hotelCode 飯店代碼
     * @return 經過編碼的訂房網址
     */
    private String createBookingUrl(String hotelCode) {
        return UriComponentsBuilder.fromUriString(BOOKING_URL)
                .queryParam("hotel", hotelCode)
                .queryParam("start", checkinDate)
                .queryParam("end", checkoutDate)
                .queryParam("room", numberOfRoom)
                .queryParam("people", numberOfPeople)
                .queryParam("smoking", smokingType)
                .queryParam("tab", "roomType")
                .queryParam("sort", "recommend")
                .build()
                .encode()
                .toUriString();
    }

    /**
     * 產生東橫 INN tRPC API 所需的查詢參數 JSON。
     *
     * @param hotelCodes 要查詢的飯店代碼
     * @return API 查詢參數的 JSON 字串
     */
    private String generateInputJson(List<String> hotelCodes) {
        return generateInputJson(hotelCodes, checkinDate, checkoutDate,
                numberOfPeople, numberOfRoom, smokingType);
    }

    static String generateInputJson(List<String> hotelCodes, LocalDate checkinDate,
            LocalDate checkoutDate, int numberOfPeople, int numberOfRoom, String smokingType) {
        String codesJsonArray = hotelCodes.stream()
                .map(code -> "\"" + code + "\"")
                .collect(Collectors.joining(","));

        return """
                {"0":{"json":{"hotelCodes":[%s],"checkinDate":"%s","checkoutDate":"%s","numberOfPeople":%d,"numberOfRoom":%d,"smokingType":"%s"},"meta":{"values":{"checkinDate":["Date"],"checkoutDate":["Date"]}}}}"""
                .formatted(
                        codesJsonArray,
                        DateTimeFormatter.ISO_INSTANT.format(
                                checkinDate.atStartOfDay(QUERY_TIME_ZONE).toInstant()),
                        DateTimeFormatter.ISO_INSTANT.format(
                                checkoutDate.atStartOfDay(QUERY_TIME_ZONE).toInstant()),
                        numberOfPeople,
                        numberOfRoom,
                        smokingType);
    }
}
