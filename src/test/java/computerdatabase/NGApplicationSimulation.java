package computerdatabase;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class NGApplicationSimulation extends Simulation {
    private static final Random random = new Random();
    private static final String CLOUD_ID = "5891aa70-018a-3bf7-9dc6-2025193262e0";
    private static final String BASE_URL = "http://localhost:8080";

    public record CreatedAssociation(String issueId, String sfObjectId) {}

    private static final List<CreatedAssociation> createdAssociations = Collections.synchronizedList(new ArrayList<>());

    private static final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL) // Root URL for all relative paths
            .header("cloudId", CLOUD_ID)
            .acceptHeader("application/json") // Common headers
            .contentTypeHeader("application/json");

    private static final Iterator<Map<String, Object>> createBodyFeeder = Stream.generate(() -> {
        var sfObjectId = UUID.randomUUID().toString();
        var jiraIssueId = RandomStringUtils.insecure().nextNumeric(3);

        createdAssociations.add(new CreatedAssociation(jiraIssueId, sfObjectId));

        return Map.of(
                "sfObjectId", (Object) sfObjectId,
                "sfObjectName", (Object) UUID.randomUUID().toString(),
                "jiraIssueId", (Object) jiraIssueId
        );
    }).iterator();

    private static final ScenarioBuilder callCreateAssociationForIssue = scenario("Call /createAssociation")
            .feed(createBodyFeeder)
            .exec(
                    http("Create association for issue")
                            .post("/createAssociation")
                            .body(StringBody("""
                                    {
                                        "sfObjectId": "#{sfObjectId}",
                                        "sfObjectName": "#{sfObjectName}",
                                        "jiraIssueId": "#{jiraIssueId}",
                                        "viewOnly": false,
                                        "autoPush": true,
                                        "autoPull": false
                                    }
                                    """))
                            .check(status().is(201))
            );

    private static final Iterator<Map<String, Object>> fetchIssueIdFeeder = Stream.generate(() -> {
        var issueId = createdAssociations.get(random.nextInt(createdAssociations.size())).issueId;
        return Map.of("issueId", (Object) issueId);
    }).iterator();

    private static final ScenarioBuilder callFetchAssociationsForIssue = scenario("Call /association/issue/{issueId}")
            .feed(fetchIssueIdFeeder)
            .exec(
                http("Fetch associations of issue")
                    .get("/association/issue/#{issueId}")
                    .check(status().is(200))
                );

    {
        setUp(
                callCreateAssociationForIssue.injectOpen(
                        constantUsersPerSec(300).during(Duration.ofMinutes(5))
                ).protocols(httpProtocol),
                callFetchAssociationsForIssue.injectOpen(
                        constantUsersPerSec(300).during(Duration.ofMinutes(5))
                ).protocols(httpProtocol)
        );
    }
}
