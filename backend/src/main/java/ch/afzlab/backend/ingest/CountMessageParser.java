package ch.afzlab.backend.ingest;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.afzlab.common.CountEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wandelt Topic und JSON-Payload eines Zaehlsensors in ein {@link CountEvent} um.
 * Erwartetes Topic: {@code afz/vehicle/<fahrzeug>/door/<tuer>/count}.
 */
@Component
public class CountMessageParser {

    private static final Pattern TOPIC = Pattern.compile("afz/vehicle/([^/]+)/door/(\\d+)/count");

    private final JsonMapper jsonMapper;

    public CountMessageParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public CountEvent parse(String topic, String payload) throws InvalidMessageException {
        Matcher m = TOPIC.matcher(topic);
        if (!m.matches()) {
            throw invalid("Unbekanntes Topic: " + topic);
        }
        String topicVehicle = m.group(1);
        int topicDoor = Integer.parseInt(m.group(2));

        JsonNode json;
        try {
            json = jsonMapper.readTree(payload);
        } catch (JacksonException e) {
            throw invalid("Kein gueltiges JSON");
        }
        if (json == null || !json.isObject()) {
            throw invalid("Payload ist kein JSON-Objekt");
        }

        String vehicleId = requireString(json, "vehicleId");
        int doorId = requireInt(json, "doorId");
        int boarding = requireInt(json, "boarding");
        int alighting = requireInt(json, "alighting");
        Instant timestamp = requireInstant(json, "timestamp");

        if (!topicVehicle.equals(vehicleId) || topicDoor != doorId) {
            throw new InvalidMessageException(RejectReason.UNPLAUSIBEL,
                    "Topic (" + topicVehicle + "/" + topicDoor + ") passt nicht zum Payload ("
                            + vehicleId + "/" + doorId + ")");
        }
        try {
            return new CountEvent(vehicleId, doorId, boarding, alighting, timestamp);
        } catch (IllegalArgumentException e) {
            throw new InvalidMessageException(RejectReason.UNPLAUSIBEL, e.getMessage());
        }
    }

    private static String requireString(JsonNode json, String field) throws InvalidMessageException {
        JsonNode node = json.get(field);
        if (node == null || !node.isString() || node.stringValue().isBlank()) {
            throw invalid("Feld '" + field + "' fehlt oder ist kein Text");
        }
        return node.stringValue();
    }

    private static int requireInt(JsonNode json, String field) throws InvalidMessageException {
        JsonNode node = json.get(field);
        if (node == null || !node.isInt()) {
            throw invalid("Feld '" + field + "' fehlt oder ist keine ganze Zahl");
        }
        return node.intValue();
    }

    private static Instant requireInstant(JsonNode json, String field) throws InvalidMessageException {
        String text = requireString(json, field);
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw invalid("Feld '" + field + "' ist kein ISO-8601-Zeitstempel: " + text);
        }
    }

    private static InvalidMessageException invalid(String message) {
        return new InvalidMessageException(RejectReason.UNGUELTIGES_FORMAT, message);
    }
}
