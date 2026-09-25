package jc.project.toyokoinn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import jc.project.toyokoinn.config.ToyokoInnProperties;
import jc.project.toyokoinn.model.RoomAlert;

class ToyokoInnEmailNotifierTests {

    private static final ZonedDateTime QUERIED_AT =
            ZonedDateTime.of(2026, 9, 25, 14, 32, 0, 0, ZoneId.of("Asia/Taipei"));

    /**
     * 驗證主旨包含飯店數量與含星期的入住日期。
     */
    @Test
    void createsSubjectWithHotelCountAndCheckinDate() {
        String subject = ToyokoInnEmailNotifier.createSubject(
                List.of(priceDropAlert(), newAvailabilityAlert()), LocalDate.of(2026, 10, 10));

        assertThat(subject).isEqualTo("【東橫 INN 空房通知】2 間飯店有空房｜2026/10/10（六）入住");
    }

    /**
     * 驗證 HTML 內容包含查詢條件、通知原因、原價格與已跳脫的訂房連結。
     */
    @Test
    void createsHtmlWithConditionsReasonsAndEscapedValues() {
        String html = ToyokoInnEmailNotifier.createHtml(
                List.of(priceDropAlert(), newAvailabilityAlert(), initialCheckAlert()),
                properties(), QUERIED_AT);

        assertThat(html)
                .contains("本次共有 3 間飯店符合通知條件")
                .contains("2026-10-10（六）")
                .contains("2026-10-11（日）・共 1 晚")
                .contains("1 間・每間 2 人")
                .contains("禁菸房")
                .contains(">價格下降<", ">新釋出空房<", ">啟動後首次查詢<")
                .contains("8,800", "9,500", "text-decoration:line-through")
                .contains("東横INN&lt;テスト&gt;")
                .doesNotContain("東横INN<テスト>")
                .contains("href=\"https://example.com/book?hotel=00001&amp;start=2026-10-10\"")
                .contains("2026-09-25 14:32（台北時間）");
    }

    /**
     * 驗證只有價格下降的飯店會顯示原價格。
     */
    @Test
    void showsPreviousPriceOnlyForPriceDrop() {
        String html = ToyokoInnEmailNotifier.createHtml(
                List.of(newAvailabilityAlert()), properties(), QUERIED_AT);

        assertThat(html).doesNotContain("text-decoration:line-through");
    }

    /**
     * 驗證純文字版本包含通知原因、價格與訂房連結。
     */
    @Test
    void createsPlainTextAlternative() {
        String text = ToyokoInnEmailNotifier.createPlainText(
                List.of(priceDropAlert()), properties(), QUERIED_AT);

        assertThat(text)
                .contains("[價格下降] 東横INN新横浜駅前本館")
                .contains("目前最低價格：8,800（原 9,500）")
                .contains("訂房連結：https://example.com/book?hotel=00001&start=2026-10-10");
    }

    /**
     * 驗證寄出的信件使用設定的寄件者、收件者與 UTF-8 主旨。
     */
    @Test
    void sendsMessageToConfiguredRecipients() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        ToyokoInnEmailNotifier notifier = new ToyokoInnEmailNotifier(mailSender, properties());

        boolean result = notifier.send(List.of(priceDropAlert()));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(result).isTrue();
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("sender@example.com");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("東橫 INN 空房通知");
        assertThat(message.getRecipients(Message.RecipientType.TO))
                .extracting(address -> ((InternetAddress) address).getAddress())
                .containsExactly("receiver@example.com", "other@example.com");
        assertThat(message.getSubject()).startsWith("【東橫 INN 空房通知】1 間飯店有空房");
    }

    /**
     * 驗證 SMTP 寄送失敗時回傳失敗，讓呼叫端保留舊價格並於下次重試。
     */
    @Test
    void returnsFalseWhenSendingFails() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("SMTP 無法連線")).when(mailSender).send(any(MimeMessage.class));
        ToyokoInnEmailNotifier notifier = new ToyokoInnEmailNotifier(mailSender, properties());

        assertThat(notifier.send(List.of(priceDropAlert()))).isFalse();
    }

    /**
     * 驗證沒有通知對象時不會建立或寄出信件。
     */
    @Test
    void doesNotSendWhenThereIsNothingToNotify() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ToyokoInnEmailNotifier notifier = new ToyokoInnEmailNotifier(mailSender, properties());

        assertThat(notifier.send(List.of())).isTrue();
        verifyNoInteractions(mailSender);
    }

    private ToyokoInnProperties properties() {
        ToyokoInnProperties properties = new ToyokoInnProperties();
        properties.setCheckinDate(LocalDate.of(2026, 10, 10));
        properties.setCheckoutDate(LocalDate.of(2026, 10, 11));
        properties.setNumberOfRoom(1);
        properties.setNumberOfPeople(2);
        properties.setSmokingType("noSmoking");
        properties.getEmail().setEnabled(true);
        properties.getEmail().setTo(List.of("receiver@example.com", " other@example.com"));
        properties.getEmail().setUsername("sender@example.com");
        properties.getEmail().setPassword("app-password");
        return properties;
    }

    private RoomAlert priceDropAlert() {
        return new RoomAlert("東横INN新横浜駅前本館", 8_800, 9_500, false,
                "https://example.com/book?hotel=00001&start=2026-10-10");
    }

    private RoomAlert newAvailabilityAlert() {
        return new RoomAlert("東横INN<テスト>", 9_200, 0, false, "https://example.com/book?hotel=00002");
    }

    private RoomAlert initialCheckAlert() {
        return new RoomAlert("東横INN横浜スタジアム前1", 10_450, 0, true, "https://example.com/book?hotel=00003");
    }
}
