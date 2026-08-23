package com.grab;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.grab.mapper")
public class GrabApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrabApplication.class, args);
    }
}
