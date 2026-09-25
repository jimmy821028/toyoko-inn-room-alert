package jc.project.toyokoinn.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.convert.ApplicationConversionService;

class ToyokoInnConfigurationValidatorTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 21);

    @Test
    void acceptsValidConfiguration() {
        ToyokoInnProperties properties = validProperties();

        List<String> errors = ToyokoInnConfigurationValidator.validate(properties, TODAY);

        assertThat(errors).isEmpty();
    }

    @Test
    void returnsEveryConfigurationErrorAtOnce() {
        ToyokoInnProperties properties = new ToyokoInnProperties();
        properties.setHotelNames(List.of(" "));
        properties.setCheckinDate(TODAY.minusDays(1));
        properties.setCheckoutDate(TODAY.minusDays(2));
        properties.setAvailabilityBatchSize(0);
        properties.setNumberOfPeople(0);
        properties.setNumberOfRoom(-1);
        properties.setSmokingType("invalid");

        List<String> errors = ToyokoInnConfigurationValidator.validate(properties, TODAY);

        assertThat(errors)
                .hasSize(7)
                .anyMatch(error -> error.contains("hotel-names"))
                .anyMatch(error -> error.contains("checkin-date 不得早於今天"))
                .anyMatch(error -> error.contains("checkout-date 必須晚於"))
                .anyMatch(error -> error.contains("availability-batch-size"))
                .anyMatch(error -> error.contains("number-of-people"))
                .anyMatch(error -> error.contains("number-of-room"))
                .anyMatch(error -> error.contains("smoking-type"));
    }

    @Test
    void reportsMissingRequiredSettingsWhenPlaceholdersAreEmpty() {
        ToyokoInnProperties properties = new Binder(
                List.of(new MapConfigurationPropertySource(Map.of(
                        "toyoko-inn.checkin-date", "",
                        "toyoko-inn.checkout-date", "",
                        "toyoko-inn.hotel-names", ""))),
                null,
                ApplicationConversionService.getSharedInstance())
                .bindOrCreate("toyoko-inn", ToyokoInnProperties.class);

        List<String> errors = ToyokoInnConfigurationValidator.validate(properties, TODAY);

        assertThat(errors).containsExactlyInAnyOrder(
                "toyoko-inn.hotel-names 至少需要一個飯店名稱",
                "toyoko-inn.checkin-date 為必填",
                "toyoko-inn.checkout-date 為必填");
    }

    @Test
    void ignoresEmptyEmailSettingsWhenEmailIsDisabled() {
        ToyokoInnProperties properties = validProperties();
        properties.getEmail().setEnabled(false);
        properties.getEmail().setSmtpPort(0);

        assertThat(ToyokoInnConfigurationValidator.validate(properties, TODAY)).isEmpty();
    }

    @Test
    void acceptsValidEmailSettings() {
        ToyokoInnProperties properties = validProperties();
        enableEmail(properties);

        assertThat(ToyokoInnConfigurationValidator.validate(properties, TODAY)).isEmpty();
    }

    @Test
    void returnsEveryEmailErrorAtOnceWhenEmailIsEnabled() {
        ToyokoInnProperties properties = validProperties();
        ToyokoInnProperties.Email email = properties.getEmail();
        email.setEnabled(true);
        email.setTo(List.of("receiver@example.com", "not-an-address"));
        email.setSmtpHost(" ");
        email.setSmtpPort(70000);
        email.setUsername("sender");
        email.setPassword("");

        List<String> errors = ToyokoInnConfigurationValidator.validate(properties, TODAY);

        assertThat(errors)
                .hasSize(5)
                .anyMatch(error -> error.contains("email.to 包含無效的電子郵件地址：not-an-address"))
                .anyMatch(error -> error.contains("email.smtp-host"))
                .anyMatch(error -> error.contains("email.smtp-port"))
                .anyMatch(error -> error.contains("email.username"))
                .anyMatch(error -> error.contains("email.password"));
    }

    @Test
    void requiresRecipientWhenEmailIsEnabled() {
        ToyokoInnProperties properties = validProperties();
        enableEmail(properties);
        properties.getEmail().setTo(List.of(" "));

        assertThat(ToyokoInnConfigurationValidator.validate(properties, TODAY))
                .containsExactly("toyoko-inn.email.to 在啟用 Email 通知時至少需要一個收件地址");
    }

    @Test
    void acceptsEverySupportedSmokingType() {
        for (String smokingType : List.of("all", "smoking", "noSmoking")) {
            ToyokoInnProperties properties = validProperties();
            properties.setSmokingType(smokingType);

            assertThat(ToyokoInnConfigurationValidator.validate(properties, TODAY))
                    .as("smoking-type=%s", smokingType)
                    .isEmpty();
        }
    }

    private ToyokoInnProperties validProperties() {
        ToyokoInnProperties properties = new ToyokoInnProperties();
        properties.setHotelNames(List.of("東横INN新横浜駅前本館"));
        properties.setCheckinDate(TODAY.plusDays(1));
        properties.setCheckoutDate(TODAY.plusDays(2));
        properties.setAvailabilityBatchSize(30);
        properties.setNumberOfPeople(1);
        properties.setNumberOfRoom(1);
        properties.setSmokingType("noSmoking");
        return properties;
    }

    private void enableEmail(ToyokoInnProperties properties) {
        ToyokoInnProperties.Email email = properties.getEmail();
        email.setEnabled(true);
        email.setTo(List.of("receiver@example.com", " other@example.com"));
        email.setUsername("sender@example.com");
        email.setPassword("app-password");
    }
}
