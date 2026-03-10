package tests;

import io.restassured.RestAssured;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import models.Order;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

/**
 * Integration test verifying End-to-End flow from REST API to Cloud RabbitMQ.
 * Docker is not required as it uses a remote CloudAMQP instance.
 */
@SpringBootTest(classes = org.example.MyApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RabbitIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(RabbitIntegrationTest.class);
    private static final String QUEUE_NAME = "automation_queue";

    @LocalServerPort
    private int port;

    // Connection URL for the remote CloudAMQP instance (includes credentials)
    private static final String CLOUD_AMQP_URL = "amqps://wrbhljju:Gt3nhDUT4t9sshJbhbj3jPeKvsbdnOgN@cow.rmq2.cloudamqp.com/wrbhljju";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Dynamically configures the Spring Application to point its RabbitTemplate to the cloud broker
        registry.add("spring.rabbitmq.addresses", () -> CLOUD_AMQP_URL);
    }

    private Connection connection;
    private Channel channel;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() throws Exception {
        // Connect the test consumer to CloudAMQP to verify message arrival
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(CLOUD_AMQP_URL);

        connection = factory.newConnection();
        channel = connection.createChannel();

        // Ensure the target queue exists on the remote broker before starting the test
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);

        // Configure RestAssured to point to the locally running Tomcat server
        RestAssured.baseURI = "http://localhost:" + port;
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up resources to prevent connection leaks
        if (channel != null && channel.isOpen()) channel.close();
        if (connection != null && connection.isOpen()) connection.close();
    }

    @Test
    @DisplayName("End-to-End: API -> Cloud RabbitMQ Verification: raise TomCat, send message through TomCatš port, get message from the queue and check content")
    void testApiToRabbitFlow() throws Exception {
        // Arrange: Prepare test data
        Order order = new Order();
        order.setId(999);
        order.setStatus("ACCEPTED");

        // Act: Send POST request to the local API endpoint (which then publishes to CloudAMQP)
        RestAssured.given()
                .contentType("application/json")
                .body(order)
                .when()
                .post("/orders")
                .then()
                .statusCode(200);

        logger.info("Sent order to API and expected it in CloudAMQP");

        // Assert: Poll the remote queue until the expected message arrives
        Awaitility.await()
                .atMost(Duration.ofSeconds(15)) // Cloud latency requires a longer timeout than local Docker
                .untilAsserted(() -> {
                    // Fetch message from the queue with auto-acknowledgement
                    GetResponse response = channel.basicGet(QUEUE_NAME, true);
                    Assertions.assertNotNull(response, "Cloud RabbitMQ queue is empty!");

                    // Deserialize the JSON body back to Order object
                    Order received = mapper.readValue(response.getBody(), Order.class);

                    // Final verification of data integrity
                    Assertions.assertEquals(order.getId(), received.getId());
                    logger.info("Successfully caught message from CloudAMQP!");
                });
    }
}