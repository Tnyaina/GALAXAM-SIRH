package mg.galaxam.sirh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SirhApplication {

	public static void main(String[] args) {
		SpringApplication.run(SirhApplication.class, args);
	}

}
