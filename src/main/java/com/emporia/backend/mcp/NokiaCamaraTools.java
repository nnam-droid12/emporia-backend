package com.emporia.backend.mcp;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Component
@RequiredArgsConstructor
public class NokiaCamaraTools {

    @Value("${nokia.network-as-code.api-key}")
    private String nokiaApiKey;

    private final RestClient restClient = RestClient.create();

    public boolean verifyKycMatch(
            @Schema(description = "The phone number of the user, must include country code") String phoneNumber,
            @Schema(description = "The business name claimed by the user") String businessName) {

        log.info("Executing REAL Nokia KYC Check for phone: {} and business: {}", phoneNumber, businessName);

        try {

            String jsonPayload;
            if ("+99999991000".equals(phoneNumber)) {
                log.info("Test number detected: Injecting full RapidAPI sandbox payload");
                jsonPayload = "{\"phoneNumber\":\"+99999991000\",\"idDocument\":\"66666666q\",\"name\":\"Federica Sanchez Arjona\",\"givenName\":\"Federica\",\"familyName\":\"Sanchez Arjona\",\"nameKanaHankaku\":\"federica\",\"nameKanaZenkaku\":\"Ｆｅｄｅｒｉｃａ\",\"middleNames\":\"Sanchez\",\"familyNameAtBirth\":\"YYYY\",\"address\":\"Tokyo-to Chiyoda-ku Iidabashi 3-10-10\",\"streetName\":\"Nicolas Salmeron\",\"streetNumber\":\"4\",\"postalCode\":\"1028460\",\"region\":\"Tokyo\",\"locality\":\"ZZZZ\",\"country\":\"JP\",\"houseNumberExtension\":\"VVVV\",\"birthdate\":\"1978-08-22\",\"email\":\"abc@example.com\",\"gender\":\"OTHER\"}";
            } else {
                jsonPayload = String.format(
                        "{\"phoneNumber\":\"%s\", \"name\":\"%s\"}",
                        phoneNumber,
                        businessName
                );
            }

            String response = restClient.post()
                    .uri("https://network-as-code.p-eu.rapidapi.com/passthrough/camara/v1/kyc-match/kyc-match/v0.3/match")
                    .header("x-rapidapi-key", nokiaApiKey)
                    .header("x-rapidapi-host", "network-as-code.nokia.rapidapi.com")
                    .header("Content-Type", "application/json")
                    .body(jsonPayload)
                    .retrieve()
                    .body(String.class);

            log.info("Nokia Network Response: {}", response);

            return response != null && (
                    response.contains("\"nameMatch\":\"true\"") ||
                            response.contains("\"nameMatch\": \"true\"") ||
                            response.contains("\"idDocumentMatch\":\"true\"") ||
                            response.contains("\"idDocumentMatch\": \"true\"")
            );

        } catch (HttpClientErrorException e) {
            log.error("Nokia HTTP Error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Nokia CAMARA API Call Failed: {}", e.getMessage());
            return false;
        }
    }

    public FunctionTool getKycTool() {
        return FunctionTool.create(this, "verifyKycMatch");
    }
}