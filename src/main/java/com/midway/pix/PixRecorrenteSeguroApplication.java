package com.midway.pix;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
        info = @Info(
                title = "Pix Recorrente Seguro API",
                version = "v1",
                description = "API para criação e consulta de agendamentos Pix recorrentes"
        )
)
@SpringBootApplication
public class PixRecorrenteSeguroApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixRecorrenteSeguroApplication.class, args);
    }
}
