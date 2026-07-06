package com.trs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.trs.**.mapper")
public class GraphMngApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphMngApplication.class, args);
    }
}
