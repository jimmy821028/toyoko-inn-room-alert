package jc.project.toyokoinn.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jc.project.toyokoinn.model.Room;
import jc.project.toyokoinn.model.RoomAlert;

class ToyokoInnRoomAlertJobTests {

    /**
     * 驗證首次成功查詢時只通知目前有空房的飯店。
     */
    @Test
    void notifiesAvailableRoomsOnFirstSuccessfulCheck() {
        Room unavailableRoom = room("00051", 0);
        Room availableRoom = room("00061", 12_000);

        List<Room> result = ToyokoInnRoomAlertJob.findRoomsToNotify(
                List.of(unavailableRoom, availableRoom), Map.of(), false);

        assertThat(result).containsExactly(availableRoom);
    }

    /**
     * 驗證原本無空房的飯店釋出房間時會觸發通知。
     */
    @Test
    void notifiesWhenRoomBecomesAvailable() {
        Room room = room("00051", 12_000);

        List<Room> result = ToyokoInnRoomAlertJob.findRoomsToNotify(
                List.of(room), Map.of("00051", 0), true);

        assertThat(result).containsExactly(room);
    }

    /**
     * 驗證目前價格低於前次價格時會觸發通知。
     */
    @Test
    void notifiesWhenPriceDrops() {
        Room room = room("00051", 10_000);

        List<Room> result = ToyokoInnRoomAlertJob.findRoomsToNotify(
                List.of(room), Map.of("00051", 12_000), true);

        assertThat(result).containsExactly(room);
    }

    /**
     * 驗證無空房、價格不變或價格上升時皆不會觸發通知。
     */
    @Test
    void doesNotNotifyWhenRoomBecomesUnavailableOrPriceDoesNotDrop() {
        List<Room> rooms = List.of(
                room("00051", 0),
                room("00061", 12_000),
                room("00120", 13_000));
        Map<String, Integer> previousPrices = Map.of(
                "00051", 10_000,
                "00061", 12_000,
                "00120", 12_000);

        List<Room> result = ToyokoInnRoomAlertJob.findRoomsToNotify(rooms, previousPrices, true);

        assertThat(result).isEmpty();
    }

    /**
     * 驗證新出現且有空房的飯店代碼會視為先前無空房並觸發通知。
     */
    @Test
    void treatsNewHotelCodeAsPreviouslyUnavailable() {
        Room room = room("00999", 9_000);

        List<Room> result = ToyokoInnRoomAlertJob.findRoomsToNotify(List.of(room), Map.of(), true);

        assertThat(result).containsExactly(room);
    }

    /**
     * 驗證只有成功送出通知的飯店才提交新價格，失敗者保留舊狀態以供重試。
     */
    @Test
    void updatesPricesOnlyAfterRequiredNotificationsSucceed() {
        Room successfulRoom = room("00051", 10_000);
        Room failedRoom = room("00061", 9_000);
        Room unavailableRoom = room("00120", 0);
        Map<String, Integer> previousPrices = new HashMap<>(Map.of(
                "00051", 12_000,
                "00061", 11_000,
                "00120", 8_000));

        ToyokoInnRoomAlertJob.updatePreviousPrices(
                List.of(successfulRoom, failedRoom, unavailableRoom),
                List.of(successfulRoom, failedRoom),
                Set.of("00051"),
                previousPrices);

        assertThat(previousPrices).containsExactlyInAnyOrderEntriesOf(Map.of(
                "00051", 10_000,
                "00061", 11_000,
                "00120", 0));
    }

    /**
     * 驗證 Email 通知資料會依該管道的前次價格判斷通知原因。
     */
    @Test
    void createsRoomAlertsWithReasonFromChannelPrices() {
        Room priceDropRoom = room("00051", 10_000);
        Room newlyAvailableRoom = room("00061", 9_000);

        List<RoomAlert> alerts = ToyokoInnRoomAlertJob.createRoomAlerts(
                List.of(priceDropRoom, newlyAvailableRoom),
                Map.of("00051", 12_000, "00061", 0),
                true,
                code -> "https://example.com/" + code);

        assertThat(alerts).containsExactly(
                new RoomAlert("00051", 10_000, 12_000, false, "https://example.com/00051"),
                new RoomAlert("00061", 9_000, 0, false, "https://example.com/00061"));
        assertThat(alerts).extracting(RoomAlert::reason).containsExactly("價格下降", "新釋出空房");
    }

    /**
     * 驗證 Email 管道首次成功查詢時，通知原因標示為啟動後首次查詢。
     */
    @Test
    void marksRoomAlertsAsInitialCheckBeforeFirstSuccessfulResult() {
        List<RoomAlert> alerts = ToyokoInnRoomAlertJob.createRoomAlerts(
                List.of(room("00051", 10_000)), Map.of(), false, code -> "https://example.com/" + code);

        assertThat(alerts).extracting(RoomAlert::reason).containsExactly("啟動後首次查詢");
    }

    /**
     * 驗證 Discord 通知會依每則訊息最多十個 embed 分批。
     */
    @Test
    void splitsDiscordNotificationsIntoBatchesOfTen() {
        List<Room> rooms = java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> room("%05d".formatted(index), 10_000 + index))
                .toList();

        List<List<Room>> batches = ToyokoInnRoomAlertJob.createDiscordBatches(rooms);

        assertThat(batches).extracting(List::size).containsExactly(10, 10, 1);
    }

    /**
     * 驗證空房 API 輸入會使用設定的人數、房間數與吸菸條件。
     */
    @Test
    void generatesInputJsonWithConfiguredRoomConditions() {
        String inputJson = ToyokoInnRoomAlertJob.generateInputJson(
                List.of("00051"),
                LocalDate.of(2027, 4, 1),
                LocalDate.of(2027, 4, 22),
                2,
                3,
                "smoking");

        assertThat(inputJson)
                .contains("\"numberOfPeople\":2")
                .contains("\"numberOfRoom\":3")
                .contains("\"smokingType\":\"smoking\"");
    }

    /**
     * 建立測試使用的房間資料。
     *
     * @param code 飯店代碼
     * @param price 最低價格
     * @return 測試用房間資料
     */
    private Room room(String code, int price) {
        Room room = new Room();
        room.setCode(code);
        room.setName(code);
        room.setLowestPrice(price);
        return room;
    }
}
