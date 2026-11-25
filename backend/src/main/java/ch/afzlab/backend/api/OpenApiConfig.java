package ch.afzlab.backend.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Titel und Beschreibung der OpenAPI-Doku (/v3/api-docs, /swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI afzOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AFZ-Lab Backend")
                .version("0.1.0")
                .description("Zaehlwerte der Fahrgastzaehlung: per MQTT empfangen, auf Plausibilitaet geprueft, "
                        + "hier lesend abrufbar."));
    }
}
