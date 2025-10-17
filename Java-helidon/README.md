# Java Helidon Sample

Small Helidon WebServer project that records user events and exposes aggregated statistics.

- **Build:** `./mvnw clean package`
- **Run:** `./mvnw exec:java -Dexec.mainClass=org.example.Nima`
- **Endpoints:**
  - `POST /event` – accepts `{"userId":"alice","value":42}`
  - `GET /stats` – returns totals and averages for all submitted events
