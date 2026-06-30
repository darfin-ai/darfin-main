package com.kosta.darfin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DarfinMainApplication {

    public static void main(String[] args) {
        SpringApplication.run(DarfinMainApplication.class, args);
    }

}
