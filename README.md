# Bajaj Finserv Health — Qualifier 1 (Java)

This Spring Boot app demonstrates the required flow:
1. Generate a webhook by contacting the vendor API.
2. Solve the SQL question (based on the last digit of the registration number).
3. Submit the final SQL query as a JSON payload to the webhook URL on application startup.

Notes:
- The app attempts the vendor endpoint `https://bhadaevapigw.healthrx.co.in/hiring/generatewebhook/JAVA`. If the vendor endpoint is unreachable from the local network, the app falls back to a safe simulation webhook (`app.override-webhook`, default: `https://httpbin.org/post`) to demonstrate the same request/response flow.
- The chosen SQL solves the provided Question 2 (odd regNo). It computes average age and returns up to 10 employee names per department for payments > 70000.

How to run (Windows PowerShell)
1) Generate (tries vendor, falls back to httpbin if network blocked):
   .\mvnw.cmd spring-boot:run

2) Solve the SQL (odd/even rule) then submit in one step:
   .\mvnw.cmd -Dapp.finalquery="YOUR_SQL_QUERY" spring-boot:run
   (Alternatively set `app.finalquery` inside `src/main/resources/application.properties` and run `.\mvnw.cmd spring-boot:run`.)

Build JAR:
   .\mvnw.cmd clean package
JAR file location:
   target/demo-0.0.1-SNAPSHOT.jar

Files included:
- Source code (Spring Boot project)
- Built JAR (demo/demo-0.0.1-SNAPSHOT.jar) — public raw link included in submission.

