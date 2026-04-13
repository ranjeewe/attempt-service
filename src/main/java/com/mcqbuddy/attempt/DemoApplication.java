package com.mcqbuddy.attempt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
        "com.mcqbuddy.bean.entity.attempt",
        "com.mcqbuddy.bean.entity.markingscheme"
})
@EnableJpaRepositories(basePackages = "com.mcqbuddy.attempt.repository")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
