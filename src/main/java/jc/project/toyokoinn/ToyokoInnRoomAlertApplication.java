package jc.project.toyokoinn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ToyokoInnRoomAlertApplication {

	/**
	 * 啟動 Spring Boot 應用程式。
	 *
	 * @param args 啟動時傳入的命令列參數
	 */
	public static void main(String[] args) {
		SpringApplication.run(ToyokoInnRoomAlertApplication.class, args);
	}

}
