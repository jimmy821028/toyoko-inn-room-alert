package jc.project.toyokoinn.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

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
}
