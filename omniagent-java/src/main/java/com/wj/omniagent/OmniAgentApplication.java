package com.wj.omniagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.wj.omniagent.modules.*.mapper")
public class OmniAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(OmniAgentApplication.class,args);
    }
}
