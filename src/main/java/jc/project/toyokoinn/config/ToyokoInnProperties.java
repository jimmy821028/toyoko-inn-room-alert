package jc.project.toyokoinn.config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toyoko-inn")
public class ToyokoInnProperties {

    private List<String> hotelNames = new ArrayList<>();
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private int numberOfPeople = 1;
    private int numberOfRoom = 1;
    private String smokingType = "noSmoking";
    private int availabilityBatchSize = 30;
    private boolean initializeCatalogOnStartup = true;
    private Discord discord = new Discord();
    private Email email = new Email();

    public List<String> getHotelNames() {
        return hotelNames;
    }

    public void setHotelNames(List<String> hotelNames) {
        this.hotelNames = hotelNames;
    }

    public LocalDate getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate(LocalDate checkinDate) {
        this.checkinDate = checkinDate;
    }

    public LocalDate getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate(LocalDate checkoutDate) {
        this.checkoutDate = checkoutDate;
    }

    public int getNumberOfPeople() {
        return numberOfPeople;
    }

    public void setNumberOfPeople(int numberOfPeople) {
        this.numberOfPeople = numberOfPeople;
    }

    public int getNumberOfRoom() {
        return numberOfRoom;
    }

    public void setNumberOfRoom(int numberOfRoom) {
        this.numberOfRoom = numberOfRoom;
    }

    public String getSmokingType() {
        return smokingType;
    }

    public void setSmokingType(String smokingType) {
        this.smokingType = smokingType;
    }

    public int getAvailabilityBatchSize() {
        return availabilityBatchSize;
    }

    public void setAvailabilityBatchSize(int availabilityBatchSize) {
        this.availabilityBatchSize = availabilityBatchSize;
    }

    public boolean isInitializeCatalogOnStartup() {
        return initializeCatalogOnStartup;
    }

    public void setInitializeCatalogOnStartup(boolean initializeCatalogOnStartup) {
        this.initializeCatalogOnStartup = initializeCatalogOnStartup;
    }

    public Discord getDiscord() {
        return discord;
    }

    public void setDiscord(Discord discord) {
        this.discord = discord;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public static class Discord {

        private String webhookUrl = "";

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }
    }

    public static class Email {

        private boolean enabled;
        private List<String> to = new ArrayList<>();
        private String smtpHost = "smtp.gmail.com";
        private int smtpPort = 587;
        private String username = "";
        private String password = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getTo() {
            return to;
        }

        public void setTo(List<String> to) {
            this.to = to;
        }

        public String getSmtpHost() {
            return smtpHost;
        }

        public void setSmtpHost(String smtpHost) {
            this.smtpHost = smtpHost;
        }

        public int getSmtpPort() {
            return smtpPort;
        }

        public void setSmtpPort(int smtpPort) {
            this.smtpPort = smtpPort;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
