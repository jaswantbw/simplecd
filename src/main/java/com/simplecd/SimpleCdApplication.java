package com.simplecd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SimpleCdApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleCdApplication.class, args);
    }
}