package com.selfservice.slotcache;

import com.selfservice.slotcache.config.SlotCacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@EnableConfigurationProperties(SlotCacheProperties.class)
public class SlotCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlotCacheApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
