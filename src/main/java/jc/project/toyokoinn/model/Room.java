package jc.project.toyokoinn.model;

import lombok.Data;

@Data
public class Room {
    // 飯店代碼（如店鋪號碼）
    private String code;
    // 飯店名稱
    private String name;
    // 該房型最低價格
    private int lowestPrice;
}
