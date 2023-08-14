package ai.kiya.process;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.JmsAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = JmsAutoConfiguration.class)
@EnableScheduling
@EnableCaching
public class CentralizedProcessApplication {

	public static void main(String[] args) {
		SpringApplication.run(CentralizedProcessApplication.class, args);
	}
	
}