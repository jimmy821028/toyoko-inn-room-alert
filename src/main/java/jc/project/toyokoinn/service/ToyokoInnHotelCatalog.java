package jc.project.toyokoinn.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jc.project.toyokoinn.config.ToyokoInnProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(1)
public class ToyokoInnHotelCatalog implements ApplicationRunner {

    private static final String HOTEL_LIST_URL = "https://www.toyoko-inn.com/hotel_list/";
    private static final Pattern HOTEL_CODE_PATTERN =
            Pattern.compile("/search/detail/(\\d{5})(?:/|[?#]|$)");

    private final RestClient restClient;
    private final ToyokoInnProperties properties;

    private volatile Map<String, String> resolvedHotels = Map.of();
    private volatile boolean ready;

    public ToyokoInnHotelCatalog(RestClient.Builder restClientBuilder,
            ToyokoInnProperties properties) {
        this.restClient = restClientBuilder.clone()
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/124.0.0.0 Safari/537.36")
                .defaultHeader("Accept-Language", "ja,en-US;q=0.8,en;q=0.7")
                .build();
        this.properties = properties;
    }

    /**
     * 啟動時取得官方飯店目錄，並一次驗證設定中的所有飯店名稱。
     *
     * @param args 應用程式啟動參數
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isInitializeCatalogOnStartup()) {
            log.debug("已停用啟動時飯店目錄初始化");
            return;
        }

        List<String> configuredHotelNames = properties.getHotelNames().stream()
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
        if (configuredHotelNames.isEmpty()) {
            log.error("未設定 toyoko-inn.hotel-names（TOYOKO_INN_HOTEL_NAMES），無法開始查詢空房");
            throw new IllegalStateException("未設定要監控的飯店名稱");
        }

        Map<String, String> hotelCodesByName = fetchHotelCodesByName();
        HotelResolution resolution = resolveHotelNames(hotelCodesByName, configuredHotelNames);
        resolution.unknownNames().forEach(name -> log.error(
                "找不到飯店名稱「{}」對應的飯店代碼，請確認 TOYOKO_INN_HOTEL_NAMES 中的名稱完全正確", name));

        if (!resolution.unknownNames().isEmpty()) {
            log.error("共有 {} 個飯店名稱無法解析，停止應用程式，不會執行空房查詢",
                    resolution.unknownNames().size());
            throw new IllegalStateException("TOYOKO_INN_HOTEL_NAMES 包含無法識別的飯店名稱");
        }

        resolvedHotels = Collections.unmodifiableMap(
                new LinkedHashMap<>(resolution.hotelsByCode()));
        ready = true;
        log.info("已完成飯店名稱驗證，共載入 {} 間飯店：{}",
                resolvedHotels.size(), resolvedHotels.values());
    }

    private Map<String, String> fetchHotelCodesByName() {
        try {
            String html = restClient.get()
                    .uri(HOTEL_LIST_URL)
                    .accept(MediaType.TEXT_HTML)
                    .retrieve()
                    .body(String.class);
            if (html == null || html.isBlank()) {
                throw new IllegalStateException("東橫 INN 飯店一覽頁未回傳內容");
            }

            Map<String, String> hotelCodesByName = parseHotelCodesByName(html);
            if (hotelCodesByName.isEmpty()) {
                throw new IllegalStateException("無法從東橫 INN 飯店一覽頁解析任何飯店");
            }
            log.info("成功取得東橫 INN 飯店目錄，共 {} 間飯店", hotelCodesByName.size());
            return hotelCodesByName;
        } catch (Exception e) {
            log.error("取得東橫 INN 飯店目錄失敗，停止應用程式", e);
            throw new IllegalStateException("無法取得東橫 INN 飯店目錄", e);
        }
    }

    static Map<String, String> parseHotelCodesByName(String html) {
        Map<String, String> hotelCodesByName = new LinkedHashMap<>();
        for (Element link : Jsoup.parse(html).select("a[href*='/search/detail/']")) {
            String name = link.text().strip();
            Matcher matcher = HOTEL_CODE_PATTERN.matcher(link.attr("href"));
            if (name.isEmpty() || !matcher.find()) {
                continue;
            }

            String hotelCode = matcher.group(1);
            String previousCode = hotelCodesByName.putIfAbsent(name, hotelCode);
            if (previousCode != null && !previousCode.equals(hotelCode)) {
                throw new IllegalStateException(
                        "飯店目錄中有重複名稱「%s」，分別對應 %s 與 %s"
                                .formatted(name, previousCode, hotelCode));
            }
        }
        return hotelCodesByName;
    }

    static HotelResolution resolveHotelNames(Map<String, String> hotelCodesByName,
            List<String> configuredHotelNames) {
        Map<String, String> hotelsByCode = new LinkedHashMap<>();
        List<String> unknownNames = configuredHotelNames.stream()
                .filter(name -> {
                    String hotelCode = hotelCodesByName.get(name);
                    if (hotelCode == null) {
                        return true;
                    }
                    hotelsByCode.put(hotelCode, name);
                    return false;
                })
                .toList();
        return new HotelResolution(hotelsByCode, unknownNames);
    }

    public boolean isReady() {
        return ready;
    }

    public Map<String, String> getResolvedHotels() {
        return resolvedHotels;
    }

    record HotelResolution(Map<String, String> hotelsByCode, List<String> unknownNames) {
    }
}
