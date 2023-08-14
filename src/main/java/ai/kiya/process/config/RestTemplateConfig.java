package ai.kiya.process.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
	
	@Value("${restcall.connect.timeout}")
	private Integer secondsConnectTimeout;
	
	@Value("${restcall.read.timeout}")
	private Integer secondsReadTimeout;
	
	@Bean
	public RestTemplate restTemplate(
	        RestTemplateBuilder restTemplateBuilder) {

	    return restTemplateBuilder
	            .setConnectTimeout(Duration.ofSeconds(secondsConnectTimeout))
	            .setReadTimeout(Duration.ofSeconds(secondsReadTimeout))
	            .build();
	}
}
