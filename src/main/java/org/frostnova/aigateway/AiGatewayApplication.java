package org.frostnova.aigateway;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@MapperScan({
        "org.frostnova.aigateway.auth.mapper",
        "org.frostnova.aigateway.conversation.mapper",
        "org.frostnova.aigateway.usage.mapper"
})
@SpringBootApplication
public class AiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
        log.info("AiGatewayApplication started!");
    }

}
