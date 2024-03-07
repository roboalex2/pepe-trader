package at.pepe.trader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PepeTraderApplication {

	public static void main(String[] args) {
		SpringApplication.run(PepeTraderApplication.class, args);
	}

}
