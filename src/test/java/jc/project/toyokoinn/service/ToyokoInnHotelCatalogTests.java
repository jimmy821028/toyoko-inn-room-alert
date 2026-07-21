package jc.project.toyokoinn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ToyokoInnHotelCatalogTests {

    @Test
    void parsesHotelNamesAndCodesFromHotelListLinks() {
        String html = """
                <html><body>
                  <a href="/search/detail/00050/">東横INN横浜桜木町</a>
                  <a href="/search/detail/00061/"> 東横INN新横浜駅前新館 </a>
                  <a href="https://example.com/other">不是飯店</a>
                </body></html>
                """;

        Map<String, String> result = ToyokoInnHotelCatalog.parseHotelCodesByName(html);

        assertThat(result).containsExactly(
                Map.entry("東横INN横浜桜木町", "00050"),
                Map.entry("東横INN新横浜駅前新館", "00061"));
    }

    @Test
    void resolvesKnownHotelsAndReturnsEveryUnknownName() {
        Map<String, String> hotelCodesByName = Map.of(
                "東横INN横浜桜木町", "00050",
                "東横INN新横浜駅前新館", "00061");

        ToyokoInnHotelCatalog.HotelResolution result = ToyokoInnHotelCatalog.resolveHotelNames(
                hotelCodesByName,
                List.of("錯誤飯店一", "東横INN横浜桜木町", "錯誤飯店二"));

        assertThat(result.hotelsByCode())
                .containsExactly(Map.entry("00050", "東横INN横浜桜木町"));
        assertThat(result.unknownNames()).containsExactly("錯誤飯店一", "錯誤飯店二");
    }

    @Test
    void rejectsOneHotelNameMappedToDifferentCodes() {
        String html = """
                <a href="/search/detail/00050/">重複飯店</a>
                <a href="/search/detail/00051/">重複飯店</a>
                """;

        assertThatThrownBy(() -> ToyokoInnHotelCatalog.parseHotelCodesByName(html))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重複飯店");
    }
}
