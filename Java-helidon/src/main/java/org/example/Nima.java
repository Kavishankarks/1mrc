package org.example;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

public final class Nima {

    public static void main(String[] args) {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        StatsService statsService = new StatsService();

        WebServer.builder()
                .port(8080)
                .routing(r -> r
                        .post("/event", (req, res) -> handleEvent(req, res, jsonMapper, statsService))
                        .get("/stats", (req, res) -> handleStats(res, jsonMapper, statsService)))
                .build()
                .start();
    }

    private static void handleEvent(ServerRequest req, ServerResponse res, JsonMapper jsonMapper, StatsService statsService) {
        try {
            Request request = jsonMapper.readValue(req.content().as(byte[].class), Request.class);
            String userId = request.userId();
            Integer value = request.value();
            statsService.recordEvent(userId, value);
            res.status(Status.NO_CONTENT_204).send();
        } catch (Exception e) {
            res.status(Status.BAD_REQUEST_400).send("Invalid JSON payload");
        }
    }

    private static void handleStats(ServerResponse res, JsonMapper jsonMapper, StatsService statsService) {
        try {
            byte[] payload = jsonMapper.writeValueAsBytes(statsService.snapshot());
            res.headers().contentType(MediaTypes.APPLICATION_JSON);
            res.send(payload);
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send("Unable to serialize stats");
        }
    }
}
