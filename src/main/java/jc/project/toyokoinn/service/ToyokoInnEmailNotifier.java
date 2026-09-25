package jc.project.toyokoinn.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import jc.project.toyokoinn.config.ToyokoInnProperties;
import jc.project.toyokoinn.model.RoomAlert;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ToyokoInnEmailNotifier {

    private static final String SENDER_NAME = "東橫 INN 空房通知";
    private static final ZoneId NOTIFICATION_TIME_ZONE = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter QUERIED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] WEEKDAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};

    private final JavaMailSender mailSender;
    private final ToyokoInnProperties properties;

    public ToyokoInnEmailNotifier(JavaMailSender mailSender, ToyokoInnProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    /**
     * 將本輪符合條件的所有飯店合併為一封 Email 寄出。
     *
     * @param alerts 要通知的飯店
     * @return 寄送成功時回傳 {@code true}
     */
    public boolean send(List<RoomAlert> alerts) {
        if (alerts.isEmpty()) {
            return true;
        }

        log.info("開始寄送 Email 通知，共 {} 筆", alerts.size());
        try {
            ToyokoInnProperties.Email email = properties.getEmail();
            ZonedDateTime queriedAt = ZonedDateTime.now(NOTIFICATION_TIME_ZONE);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(email.getUsername().strip(), SENDER_NAME,
                    StandardCharsets.UTF_8.name()));
            helper.setTo(email.getTo().stream()
                    .filter(address -> address != null && !address.isBlank())
                    .map(String::strip)
                    .toArray(String[]::new));
            helper.setSubject(createSubject(alerts, properties.getCheckinDate()));
            helper.setText(createPlainText(alerts, properties, queriedAt),
                    createHtml(alerts, properties, queriedAt));
            mailSender.send(message);
            log.info("Email 通知發送成功，共 {} 筆", alerts.size());
            return true;
        } catch (Exception e) {
            log.error("發送 Email 通知失敗", e);
            return false;
        }
    }

    static String createSubject(List<RoomAlert> alerts, LocalDate checkinDate) {
        return "【東橫 INN 空房通知】%d 間飯店有空房｜%s入住".formatted(
                alerts.size(), formatDate(checkinDate).replace('-', '/'));
    }

    static String createPlainText(List<RoomAlert> alerts, ToyokoInnProperties properties,
            ZonedDateTime queriedAt) {
        StringBuilder text = new StringBuilder();
        text.append("有空房釋出！本次共有 %d 間飯店符合通知條件。%n%n".formatted(alerts.size()));
        text.append("入住日期：%s%n".formatted(formatDate(properties.getCheckinDate())));
        text.append("退房日期：%s・共 %d 晚%n".formatted(
                formatDate(properties.getCheckoutDate()), countNights(properties)));
        text.append("房數／人數：%d 間・每間 %d 人%n".formatted(
                properties.getNumberOfRoom(), properties.getNumberOfPeople()));
        text.append("吸菸條件：%s%n".formatted(smokingTypeLabel(properties.getSmokingType())));

        for (RoomAlert alert : alerts) {
            text.append("%n[%s] %s%n".formatted(alert.reason(), alert.hotelName()));
            text.append("目前最低價格：%,d".formatted(alert.lowestPrice()));
            if (alert.isPriceDrop()) {
                text.append("（原 %,d）".formatted(alert.previousPrice()));
            }
            text.append("，幣別以訂房頁為準%n".formatted());
            text.append("訂房連結：%s%n".formatted(alert.bookingUrl()));
        }

        text.append("%n查詢時間：%s（台北時間）%n".formatted(queriedAt.format(QUERIED_AT_FORMATTER)));
        text.append("空房與價格可能隨時變動，實際情況以東橫 INN 訂房頁為準。%n".formatted());
        return text.toString();
    }

    static String createHtml(List<RoomAlert> alerts, ToyokoInnProperties properties,
            ZonedDateTime queriedAt) {
        StringBuilder cards = new StringBuilder();
        for (int index = 0; index < alerts.size(); index++) {
            cards.append(createHotelCard(alerts.get(index), index == 0));
        }

        RoomAlert firstAlert = alerts.getFirst();
        String preheader = "%s 最低 %,d 起".formatted(firstAlert.hotelName(), firstAlert.lowestPrice())
                + (alerts.size() > 1 ? "，另有 %d 間飯店有空房".formatted(alerts.size() - 1) : "");

        return """
                <!DOCTYPE html>
                <html lang="zh-Hant">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="light">
                <title>東橫 INN 空房通知</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f2f4f7;">
                  <div style="display:none; max-height:0; overflow:hidden; mso-hide:all;">%s</div>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f2f4f7;">
                    <tr>
                      <td align="center" style="padding:24px 12px;">
                        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%%; max-width:600px; background-color:#ffffff; border-radius:8px; overflow:hidden; font-family:'Microsoft JhengHei','PingFang TC','Noto Sans TC',Arial,sans-serif; color:#1f2933;">
                          <tr>
                            <td style="background-color:#1f3a5f; padding:24px 28px;">
                              <div style="font-size:13px; color:#b8c7dc; letter-spacing:1px;">TOYOKO INN ROOM ALERT</div>
                              <div style="font-size:22px; font-weight:bold; color:#ffffff; margin-top:6px;">有空房釋出！</div>
                              <div style="font-size:14px; color:#dce5f0; margin-top:6px;">本次共有 %d 間飯店符合通知條件</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 28px 4px 28px;">
                              <div style="font-size:13px; font-weight:bold; color:#52606d; margin-bottom:8px;">查詢條件</div>
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f7f9fb; border:1px solid #e4e7eb; border-radius:6px; font-size:14px;">
                                <tr>
                                  <td style="padding:10px 14px; color:#7b8794; width:90px;">入住日期</td>
                                  <td style="padding:10px 14px;">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:0 14px 10px 14px; color:#7b8794;">退房日期</td>
                                  <td style="padding:0 14px 10px 14px;">%s・共 %d 晚</td>
                                </tr>
                                <tr>
                                  <td style="padding:0 14px 10px 14px; color:#7b8794;">房數／人數</td>
                                  <td style="padding:0 14px 10px 14px;">%d 間・每間 %d 人</td>
                                </tr>
                                <tr>
                                  <td style="padding:0 14px 10px 14px; color:#7b8794;">吸菸條件</td>
                                  <td style="padding:0 14px 10px 14px;">%s</td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                %s
                          <tr>
                            <td style="padding:24px 28px 26px 28px;">
                              <div style="border-top:1px solid #e4e7eb; padding-top:16px; font-size:12px; line-height:1.7; color:#9aa5b1;">
                                查詢時間：%s（台北時間）<br>
                                空房與價格可能隨時變動，實際情況以東橫 INN 訂房頁為準。<br>
                                本信件由 toyoko-inn-room-alert 自動寄出，請勿直接回覆。
                              </div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(preheader),
                alerts.size(),
                formatDate(properties.getCheckinDate()),
                formatDate(properties.getCheckoutDate()),
                countNights(properties),
                properties.getNumberOfRoom(),
                properties.getNumberOfPeople(),
                smokingTypeLabel(properties.getSmokingType()),
                cards,
                queriedAt.format(QUERIED_AT_FORMATTER));
    }

    private static String createHotelCard(RoomAlert alert, boolean first) {
        String badgeStyle = switch (alert.reason()) {
            case "價格下降" -> "color:#b44d12; background-color:#fff1e6;";
            case "新釋出空房" -> "color:#1a7f3c; background-color:#e6f6ec;";
            default -> "color:#1f5f9e; background-color:#e8f1fb;";
        };
        String previousPrice = alert.isPriceDrop()
                ? "<span style=\"font-size:14px; color:#9aa5b1; text-decoration:line-through; margin-left:8px;\">%,d</span>"
                        .formatted(alert.previousPrice())
                : "";

        return """
                          <tr>
                            <td style="padding:%s 28px 0 28px;">
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #e4e7eb; border-left:4px solid #1a9d4b; border-radius:6px;">
                                <tr>
                                  <td style="padding:16px 18px;">
                                    <span style="display:inline-block; font-size:12px; font-weight:bold; %s border-radius:10px; padding:2px 10px;">%s</span>
                                    <div style="font-size:17px; font-weight:bold; margin-top:8px;">%s</div>
                                    <div style="margin-top:10px; font-size:13px; color:#7b8794;">目前最低價格</div>
                                    <div style="margin-top:2px;">
                                      <span style="font-size:24px; font-weight:bold; color:#1a9d4b;">%,d</span>%s
                                    </div>
                                    <div style="font-size:12px; color:#9aa5b1; margin-top:2px;">幣別以訂房頁為準</div>
                                    <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin-top:14px;">
                                      <tr>
                                        <td style="background-color:#1a9d4b; border-radius:5px;">
                                          <a href="%s" style="display:inline-block; padding:10px 22px; font-size:14px; font-weight:bold; color:#ffffff; text-decoration:none;">立即訂房 →</a>
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                """.formatted(
                first ? "16px" : "12px",
                badgeStyle,
                escape(alert.reason()),
                escape(alert.hotelName()),
                alert.lowestPrice(),
                previousPrice,
                escape(alert.bookingUrl()));
    }

    private static String formatDate(LocalDate date) {
        return "%s（%s）".formatted(date, WEEKDAY_NAMES[date.getDayOfWeek().getValue() - 1]);
    }

    private static long countNights(ToyokoInnProperties properties) {
        return ChronoUnit.DAYS.between(properties.getCheckinDate(), properties.getCheckoutDate());
    }

    private static String smokingTypeLabel(String smokingType) {
        return switch (smokingType) {
            case "smoking" -> "吸菸房";
            case "noSmoking" -> "禁菸房";
            default -> "不限";
        };
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }
}
