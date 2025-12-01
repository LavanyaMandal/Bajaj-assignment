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

    // Overrides used when real API is unreachable (safe simulation)
    @Value("${app.override-webhook:https://httpbin.org/post}")
    private String overrideWebhook;

    @Value("${app.override-token:SIMULATED_TOKEN}")
    private String overrideToken;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("➡ Starting webhook flow...");

        String webhookUrl = null;
        String accessToken = null;

        // 1) Try to call the real generate endpoint
        try {
            Map<String, String> body = Map.of(
                    "name", name,
                    "regNo", regNo,
                    "email", email
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            System.out.println("Trying to call generate endpoint: " + generateUrl);
            ResponseEntity<Map> response = restTemplate.postForEntity(generateUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> resp = response.getBody();
                System.out.println("Generate response (raw): " + resp);

                webhookUrl = resp.containsKey("webhookUrl") ? (String) resp.get("webhookUrl")
                        : (String) resp.getOrDefault("webhook", null);
                accessToken = resp.containsKey("accessToken") ? (String) resp.get("accessToken")
                        : (String) resp.getOrDefault("token", null);

                if (webhookUrl == null || accessToken == null) {
                    System.out.println("Warning: expected keys not found in response. Will fall back to override values.");
                } else {
                    System.out.println("Received webhookUrl and accessToken from real API.");
                }
            } else {
                System.out.println("Generate endpoint did not return usable body. Falling back to overrides.");
            }

        } catch (ResourceAccessException rae) {
            // network / DNS / host unreachable
            System.out.println("Network error when calling generate endpoint: " + rae.getMessage());
            System.out.println("Falling back to simulated override values.");
        } catch (HttpClientErrorException httpEx) {
            System.out.println("HTTP error from generate endpoint: " + httpEx.getStatusCode() + " - " + httpEx.getResponseBodyAsString());
            System.out.println("Falling back to simulated override values.");
        } catch (Exception ex) {
            System.out.println("Unexpected error when calling generate endpoint: " + ex.getMessage());
            System.out.println("Falling back to simulated override values.");
        }

        // If nothing from real API, use the override webhook & token
        if (webhookUrl == null || webhookUrl.isBlank()) {
            webhookUrl = overrideWebhook != null && !overrideWebhook.isBlank() ? overrideWebhook : submitUrl;
            accessToken = overrideToken;
            System.out.println("Using override webhook URL: " + webhookUrl);
            System.out.println("Using override access token (truncated): " + (accessToken.length() > 20 ? accessToken.substring(0, 20) + "..." : accessToken));
        }

        // Show regNo info (odd/even)
        char lastChar = regNo.trim().charAt(regNo.trim().length() - 1);
        int lastDigit = Character.isDigit(lastChar) ? Character.getNumericValue(lastChar) : -1;
        System.out.println("regNo last digit: " + lastDigit + " -> " + (lastDigit % 2 == 0 ? "EVEN" : "ODD"));

        // Instruction to the user (manual solve step)
        System.out.println("\n--- NEXT (you): ---");
        System.out.println("1) Open the question URL from the PDF based on EVEN/ODD and solve the SQL.");
        System.out.println("2) Re-run this app with -Dapp.finalquery=\"YOUR_SQL_QUERY\" to auto-submit.");
        System.out.println("   Example (Windows PowerShell): .\\mvnw.cmd spring-boot:run -Dapp.finalquery=\"SELECT ...;\"\n");

        // If final query provided as system property, auto-submit now
        String finalQuery = System.getProperty("app.finalquery");
        if (finalQuery != null && !finalQuery.isBlank()) {
            System.out.println("Auto-submit requested. Submitting final query now...");
            submitFinalQuery(webhookUrl, accessToken, finalQuery);
        } else {
            System.out.println("No final query provided. Solve SQL and re-run with -Dapp.finalquery to submit.");
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
            System.err.println("If this happens, you can re-run using a different network or keep using overrides.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
