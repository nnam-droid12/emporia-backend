package com.emporia.backend.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class NokiaCamaraTools {

    @Value("${nokia.network-as-code.api-key}")
    private String nokiaApiKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String verifyKycMatch(String phoneNumber, String businessName) {
        log.info("Executing REAL Nokia KYC Check for phone: {} and business: {}", phoneNumber, businessName);

        try {
            // Hackathon Sandbox bypass
            String jsonPayload;
            if ("+99999991000".equals(phoneNumber)) {
                jsonPayload = "{\"phoneNumber\":\"+99999991000\",\"idDocument\":\"66666666q\",\"name\":\"Federica Sanchez Arjona\",\"givenName\":\"Federica\",\"familyName\":\"Sanchez Arjona\",\"nameKanaHankaku\":\"federica\",\"nameKanaZenkaku\":\"Ｆｅｄｅｒｉｃａ\",\"middleNames\":\"Sanchez\",\"familyNameAtBirth\":\"YYYY\",\"address\":\"Tokyo-to Chiyoda-ku Iidabashi 3-10-10\",\"streetName\":\"Nicolas Salmeron\",\"streetNumber\":\"4\",\"postalCode\":\"1028460\",\"region\":\"Tokyo\",\"locality\":\"ZZZZ\",\"country\":\"JP\",\"houseNumberExtension\":\"VVVV\",\"birthdate\":\"1978-08-22\",\"email\":\"abc@example.com\",\"gender\":\"OTHER\"}";
            } else {
                jsonPayload = String.format("{\"phoneNumber\":\"%s\", \"name\":\"%s\"}", phoneNumber, businessName);
            }

            String response = restClient.post()
                    .uri("https://network-as-code.p-eu.rapidapi.com/passthrough/camara/v1/kyc-match/kyc-match/v0.3/match")
                    .header("x-rapidapi-key", nokiaApiKey)
                    .header("x-rapidapi-host", "network-as-code.nokia.rapidapi.com")
                    .header("Content-Type", "application/json")
                    .body(jsonPayload)
                    .retrieve()
                    .body(String.class);

            // Fast, reliable Java parsing
            if (response != null && (response.contains("\"nameMatch\":\"true\"") || response.contains("\"idDocumentMatch\":\"true\"") || response.contains("\"nameMatch\": \"true\"") || response.contains("\"idDocumentMatch\": \"true\""))) {

                // Try to extract address if it exists in the dummy payload
                String address = "Address verified via Telecom";
                if ("+99999991000".equals(phoneNumber)) {
                    try {
                        JsonNode rootNode = objectMapper.readTree(jsonPayload);
                        if (rootNode.has("address")) {
                            address = rootNode.get("address").asText();
                        }
                    } catch (Exception e) {
                        log.warn("Could not parse address from dummy payload");
                    }
                }
                return "APPROVED | " + address;
            }
            return "REJECTED";

        } catch (Exception e) {
            log.error("Nokia CAMARA API Call Failed: {}", e.getMessage());
            return "REJECTED";
        }
    }
}