package com.tanxian;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.tanxian.mapper")
public class HuanyuApplication {
    public static void main(String[] args) {
        SpringApplication.run(HuanyuApplication.class, args);
    }
}
