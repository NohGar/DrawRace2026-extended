package backend.drawrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DrawRaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DrawRaceApplication.class, args);
    }
}
