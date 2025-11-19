package com.capitecfilestatement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class CapitecFileStatementApplication {

    public static void main(String[] args) {
        SpringApplication.run(CapitecFileStatementApplication.class, args);
    }

}
