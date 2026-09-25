package jc.project.toyokoinn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jc.project.toyokoinn.config.ToyokoInnProperties;

@SpringBootTest(properties = {
        "toyoko-inn.initialize-catalog-on-startup=false",
        "toyoko-inn.checkin-date=2099-01-01",
        "toyoko-inn.checkout-date=2099-01-02",
        "toyoko-inn.hotel-names=Test Hotel"
})
class ToyokoInnRoomAlertApplicationTests {

    @Autowired
    private ToyokoInnProperties properties;

    /**
     * 驗證只提供必填設定時，Spring 應用程式內容能夠載入，且選填設定套用安全預設值。
     */
    @Test
    void contextLoadsWithOptionalSettingsDefaulted() {
        assertThat(properties.getNumberOfPeople()).isEqualTo(1);
        assertThat(properties.getNumberOfRoom()).isEqualTo(1);
        assertThat(properties.getSmokingType()).isEqualTo("noSmoking");
        assertThat(properties.getAvailabilityBatchSize()).isEqualTo(30);
        assertThat(properties.getDiscord().getWebhookUrl()).isEmpty();
    }

}
