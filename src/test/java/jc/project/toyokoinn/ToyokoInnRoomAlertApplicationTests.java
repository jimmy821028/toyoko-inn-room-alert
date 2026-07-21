package jc.project.toyokoinn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "toyoko-inn.initialize-catalog-on-startup=false",
        "toyoko-inn.checkin-date=2099-01-01",
        "toyoko-inn.checkout-date=2099-01-02"
})
class ToyokoInnRoomAlertApplicationTests {

	/**
	 * 驗證 Spring 應用程式內容能夠正常載入。
	 */
	@Test
	void contextLoads() {
	}

}
