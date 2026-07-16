package com.trs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.trs.**.mapper")
@EnableAspectJAutoProxy
public class GraphMngApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphMngApplication.class, args);
    }
}
