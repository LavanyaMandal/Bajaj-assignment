package com.example.demo;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/*
 * WebhookStartupRunner
 *
 * On application start:
 * 1) Attempt to generate a webhook by calling the vendor endpoint.
 * 2) If the vendor endpoint is unreachable or doesn't return expected data,
 *    fall back to a safe simulated webhook (httpbin) using override properties.
 * 3) If a final query is provided via -Dapp.finalquery or application.properties,
 *    submit it to the webhook.
 *
 * Clear, simple, human-written style.
 */
@Component
public class WebhookStartupRunner implements CommandLineRunner {

    private final RestTemplate restTemplate;

    public WebhookStartupRunner(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${app.name}")
    private String name;

    @Value("${app.email}")
    private String email;

    @Value("${app.regno}")
    private String regNo;

    @Value("${app.generate-url}")
    private String generateUrl;

    @Value("${app.submit-url}")
    private String submitUrl;

    // Fallback values used when the real vendor API can't be reached
    @Value("${app.override-webhook:https://httpbin.org/post}")
    private String overrideWebhook;

    @Value("${app.override-token:SIMULATED_TOKEN}")
    private String overrideToken;

    // Optional: allow final query to be provided from application.properties if CLI quoting is difficult
    @Value("${app.finalquery:}")
    private String finalQueryFromProperties;

    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>> Starting webhook flow");

        String webhookUrl = null;
        String accessToken = null;

        // Try the vendor "generate webhook" endpoint first
        try {
            Map<String, String> body = Map.of(
                    "name", name,
                    "regNo", regNo,
                    "email", email
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            System.out.println("Calling generate endpoint: " + generateUrl);
            ResponseEntity<Map> response = restTemplate.postForEntity(generateUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> resp = response.getBody();
                System.out.println("Generate response (raw): " + resp);

                webhookUrl = resp.containsKey("webhookUrl") ? (String) resp.get("webhookUrl")
                        : (String) resp.getOrDefault("webhook", null);
                accessToken = resp.containsKey("accessToken") ? (String) resp.get("accessToken")
                        : (String) resp.getOrDefault("token", null);

                if (webhookUrl != null && accessToken != null) {
                    System.out.println("Received webhook URL and access token from vendor.");
                } else {
                    System.out.println("Vendor did not return full data. Will use fallback values.");
                }
            } else {
                System.out.println("Vendor response not usable. Falling back to overrides.");
            }

        } catch (ResourceAccessException rae) {
            // network / DNS / host unreachable
            System.out.println("Network error calling vendor: " + rae.getMessage());
            System.out.println("Falling back to configured override webhook.");
        } catch (HttpClientErrorException httpEx) {
            System.out.println("HTTP error from vendor: " + httpEx.getStatusCode() + " - " + httpEx.getResponseBodyAsString());
            System.out.println("Falling back to configured override webhook.");
        } catch (Exception ex) {
            System.out.println("Unexpected error while calling vendor: " + ex.getMessage());
            System.out.println("Falling back to configured override webhook.");
        }

        // Use override values if vendor data wasn't available
        if (webhookUrl == null || webhookUrl.isBlank()) {
            webhookUrl = (overrideWebhook != null && !overrideWebhook.isBlank()) ? overrideWebhook : submitUrl;
            accessToken = overrideToken;
            System.out.println("Using override webhook: " + webhookUrl);
            System.out.println("Using override token (truncated): " + (accessToken.length() > 20 ? accessToken.substring(0, 20) + "..." : accessToken));
        }

        // Show whether we got an odd/even reg number assignment
        char lastChar = regNo.trim().charAt(regNo.trim().length() - 1);
        int lastDigit = Character.isDigit(lastChar) ? Character.getNumericValue(lastChar) : -1;
        System.out.println("Registration number last digit: " + lastDigit + " -> " + (lastDigit % 2 == 0 ? "EVEN" : "ODD"));

        // Instructions for the person running the app
        System.out.println("\n--- Next steps for you ---");
        System.out.println("1) Open the assignment PDF and solve the SQL for your assigned question (odd/even).");
        System.out.println("2) Re-run with the final SQL using -Dapp.finalquery=\"YOUR_SQL_QUERY\" to auto-submit.");
        System.out.println("   Example (PowerShell): .\\mvnw.cmd -Dapp.finalquery=\"SELECT ...\" spring-boot:run\n");

        // If final query is passed as a system property, prefer it; otherwise use the properties value
        String finalQuery = System.getProperty("app.finalquery");
        if (finalQuery == null || finalQuery.isBlank()) {
            finalQuery = this.finalQueryFromProperties;
        }

        if (finalQuery != null && !finalQuery.isBlank()) {
            System.out.println("Auto-submit requested. Submitting final query now...");
            submitFinalQuery(webhookUrl, accessToken, finalQuery);
        } else {
            System.out.println("No final query provided. Solve the SQL and re-run with -Dapp.finalquery to submit.");
        }
    }

    private void submitFinalQuery(String webhookUrl, String accessToken, String finalQuery) {
        try {
            HttpHeaders submitHeaders = new HttpHeaders();
            submitHeaders.setContentType(MediaType.APPLICATION_JSON);
            submitHeaders.set("Authorization", accessToken);

            Map<String, String> submitBody = Map.of("finalquery", finalQuery);
            HttpEntity<Map<String, String>> submitRequest = new HttpEntity<>(submitBody, submitHeaders);

            System.out.println("Submitting to: " + webhookUrl);
            System.out.println("Submit body: " + submitBody);

            ResponseEntity<String> submitResp = restTemplate.postForEntity(webhookUrl, submitRequest, String.class);

            System.out.println("Submit response status: " + submitResp.getStatusCode());
            System.out.println("Submit response body: " + submitResp.getBody());
        } catch (HttpClientErrorException e) {
            System.err.println("Submit HTTP error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (ResourceAccessException rae) {
            System.err.println("Network error while submitting: " + rae.getMessage());
            System.err.println("You can re-run on a different network or rely on the documented fallback.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
