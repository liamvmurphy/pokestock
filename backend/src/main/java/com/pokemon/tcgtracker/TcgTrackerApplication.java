package com.pokemon.tcgtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TcgTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TcgTrackerApplication.class, args);
    }

}