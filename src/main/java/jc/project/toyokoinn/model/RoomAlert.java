package jc.project.toyokoinn.model;

/**
 * Email 通知中單一飯店的顯示資料。
 *
 * @param hotelName 飯店名稱
 * @param lowestPrice 目前最低價格
 * @param previousPrice 此通知管道前次記錄的最低價格，零表示先前無空房
 * @param initialCheck 是否為此通知管道啟動後的首次成功查詢
 * @param bookingUrl 帶入查詢條件的訂房網址
 */
public record RoomAlert(
        String hotelName,
        int lowestPrice,
        int previousPrice,
        boolean initialCheck,
        String bookingUrl) {

    /**
     * 判斷此通知是否因價格下降而觸發。
     *
     * @return 價格下降時回傳 {@code true}
     */
    public boolean isPriceDrop() {
        return !initialCheck && previousPrice > 0 && lowestPrice < previousPrice;
    }

    /**
     * 取得通知原因的顯示文字。
     *
     * @return 通知原因
     */
    public String reason() {
        if (initialCheck) {
            return "啟動後首次查詢";
        }
        return isPriceDrop() ? "價格下降" : "新釋出空房";
    }
}
