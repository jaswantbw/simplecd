// Copyright (c) 2026 Jaswant Sharma. All rights reserved.
// SimpleCD - https://github.com/jaswantbw/simplecd
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