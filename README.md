# Bajaj Finserv Health — Qualifier 1 (Java)

This is a small Spring Boot app that demonstrates the required workflow:
1. Attempt to generate a webhook by calling the vendor API.
2. If the vendor API is unreachable from the network, fall back to a safe simulation endpoint to show the same request/response flow.
3. Submit a final SQL query (provided at runtime) to the webhook using an Authorization header.

How to run (PowerShell)
1) Generate (tries vendor, falls back if blocked):
   .\mvnw.cmd spring-boot:run

2) Solve the SQL (odd/even rule based on last digit of regNo) and submit in one step:
   - If quoting is simple, run:
     .\mvnw.cmd -Dapp.finalquery="YOUR_SQL_QUERY" spring-boot:run
   - Or set `app.finalquery` in `src/main/resources/application.properties` and run:
     .\mvnw.cmd spring-boot:run

Build JAR:
   .\mvnw.cmd clean package
JAR location:
   demo/target/demo-0.0.1-SNAPSHOT.jar
Public JAR link:
   https://github.com/LavanyaMandal/Bajaj-assignment/raw/main/demo/demo-0.0.1-SNAPSHOT.jar

Notes:
- The app uses `RestTemplate` and runs as a CommandLineRunner (no controllers).
- For local networks that block the vendor host, the app uses `app.override-webhook` (default: https://httpbin.org/post) to simulate submission; this behavior is documented here.
- Replace `app.name`, `app.email`, and `app.regno` in `application.properties` with your real details before running.

Author: Lavanya Mandal
Email: lavanyamandal2203@gmail.com
