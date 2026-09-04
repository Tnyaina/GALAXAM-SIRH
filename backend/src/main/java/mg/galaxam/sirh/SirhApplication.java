package mg.galaxam.sirh;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class SirhApplication {

	public static void main(String[] args) {
		SpringApplication.run(SirhApplication.class, args);
	}

	@PostConstruct
    public void initTimezone() {
        // Force l'application à utiliser le fuseau horaire de Madagascar (UTC+3)
        TimeZone.setDefault(TimeZone.getTimeZone("Indian/Antananarivo"));
        System.out.println("TimeZone initialisée sur : " + TimeZone.getDefault().getID());
    }

}
