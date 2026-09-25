package jc.project.toyokoinn.config;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(0)
public class ToyokoInnConfigurationValidator implements ApplicationRunner {

    private static final ZoneId APPLICATION_TIME_ZONE = ZoneId.of("Asia/Taipei");
    private static final Set<String> ALLOWED_SMOKING_TYPES =
            Set.of("all", "smoking", "noSmoking");

    private final ToyokoInnProperties properties;

    public ToyokoInnConfigurationValidator(ToyokoInnProperties properties) {
        this.properties = properties;
    }

    /**
     * 啟動時一次驗證所有本機設定，避免以無效條件呼叫外部服務。
     *
     * @param args 應用程式啟動參數
     */
    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = validate(properties, LocalDate.now(APPLICATION_TIME_ZONE));
        errors.forEach(error -> log.error("設定錯誤：{}", error));
        if (!errors.isEmpty()) {
            throw new IllegalStateException("toyoko-inn 設定無效，請檢查對應的 TOYOKO_INN_* 環境變數");
        }
    }

    static List<String> validate(ToyokoInnProperties properties, LocalDate today) {
        List<String> errors = new ArrayList<>();
        LocalDate checkinDate = properties.getCheckinDate();
        LocalDate checkoutDate = properties.getCheckoutDate();

        if (properties.getHotelNames() == null
                || properties.getHotelNames().stream().allMatch(name -> name == null || name.isBlank())) {
            errors.add("toyoko-inn.hotel-names 至少需要一個飯店名稱");
        }
        if (checkinDate == null) {
            errors.add("toyoko-inn.checkin-date 為必填");
        } else if (checkinDate.isBefore(today)) {
            errors.add("toyoko-inn.checkin-date 不得早於今天 " + today);
        }
        if (checkoutDate == null) {
            errors.add("toyoko-inn.checkout-date 為必填");
        } else if (checkinDate != null && !checkoutDate.isAfter(checkinDate)) {
            errors.add("toyoko-inn.checkout-date 必須晚於 toyoko-inn.checkin-date");
        }
        if (properties.getAvailabilityBatchSize() <= 0) {
            errors.add("toyoko-inn.availability-batch-size 必須大於 0");
        }
        if (properties.getNumberOfPeople() <= 0) {
            errors.add("toyoko-inn.number-of-people 必須大於 0");
        }
        if (properties.getNumberOfRoom() <= 0) {
            errors.add("toyoko-inn.number-of-room 必須大於 0");
        }
        if (properties.getSmokingType() == null
                || !ALLOWED_SMOKING_TYPES.contains(properties.getSmokingType())) {
            errors.add("toyoko-inn.smoking-type 只能是 all、smoking 或 noSmoking");
        }
        if (properties.getEmail().isEnabled()) {
            validateEmail(properties.getEmail(), errors);
        }
        return errors;
    }

    private static void validateEmail(ToyokoInnProperties.Email email, List<String> errors) {
        List<String> recipients = email.getTo() == null ? List.of() : email.getTo().stream()
                .filter(address -> address != null && !address.isBlank())
                .map(String::strip)
                .toList();
        if (recipients.isEmpty()) {
            errors.add("toyoko-inn.email.to 在啟用 Email 通知時至少需要一個收件地址");
        }
        recipients.stream()
                .filter(address -> !isValidEmailAddress(address))
                .forEach(address -> errors.add("toyoko-inn.email.to 包含無效的電子郵件地址：" + address));
        if (email.getSmtpHost() == null || email.getSmtpHost().isBlank()) {
            errors.add("toyoko-inn.email.smtp-host 在啟用 Email 通知時為必填");
        }
        if (email.getSmtpPort() < 1 || email.getSmtpPort() > 65535) {
            errors.add("toyoko-inn.email.smtp-port 必須介於 1 到 65535");
        }
        if (email.getUsername() == null || email.getUsername().isBlank()) {
            errors.add("toyoko-inn.email.username 在啟用 Email 通知時為必填");
        } else if (!isValidEmailAddress(email.getUsername().strip())) {
            errors.add("toyoko-inn.email.username 會作為寄件地址，必須是有效的電子郵件地址");
        }
        if (email.getPassword() == null || email.getPassword().isBlank()) {
            errors.add("toyoko-inn.email.password 在啟用 Email 通知時為必填");
        }
    }

    private static boolean isValidEmailAddress(String address) {
        try {
            InternetAddress internetAddress = new InternetAddress(address, true);
            internetAddress.validate();
            return internetAddress.getPersonal() == null && address.equals(internetAddress.getAddress());
        } catch (AddressException e) {
            return false;
        }
    }
}
