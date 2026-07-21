package jc.project.toyokoinn.model;

import java.util.Map;

@lombok.Data
public class HotelsAvailabilitiesPrices {
    // 查詢結果主體
    private Result result;

    @lombok.Data
    public static class Result {
        // 回傳的資料節點
        private Data data;
    }

    @lombok.Data
    public static class Data {
        // JSON 內容主體
        private Json json;
    }

    @lombok.Data
    public static class Json {
        // 依房型代碼分組的價格資訊
        private Map<String, PriceInfo> prices;
    }

    @lombok.Data
    public static class PriceInfo {
        // 該房型最低價格
        private int lowestPrice;
        // 是否有足夠空房
        private boolean existEnoughVacantRooms;
        // 是否為維修中
        private boolean isUnderMaintenance;
    }
}
