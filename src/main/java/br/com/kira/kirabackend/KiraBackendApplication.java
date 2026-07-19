package br.com.kira.kirabackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class KiraBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(KiraBackendApplication.class, args);
    }
}