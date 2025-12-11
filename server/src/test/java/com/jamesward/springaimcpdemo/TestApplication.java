package com.jamesward.springaimcpdemo;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

@SpringBootApplication
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public ApplicationRunner applicationRunner(FlightSearchService flightSearchService) {
        return args -> {
            var flights = flightSearchService.searchFlights("DEN", "SFO", LocalDateTime.now().plusDays(2), null);
            System.out.println(flights);
        };
    }
}
