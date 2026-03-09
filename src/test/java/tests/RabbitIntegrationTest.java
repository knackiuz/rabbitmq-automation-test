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

// 1. Убрали @Testcontainers, так как теперь работаем через облако
@SpringBootTest(classes = org.example.MyApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RabbitIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(RabbitIntegrationTest.class);
    private static final String QUEUE_NAME = "automation_queue";

    @LocalServerPort
    private int port;

    // 2. ЗАМЕНИ ЭТУ СТРОКУ на свой реальный URL из панели CloudAMQP
    private static final String CLOUD_AMQP_URL = "amqps://wrbhljju:Gt3nhDUT4t9sshJbhbj3jPeKvsbdnOgN@cow.rmq2.cloudamqp.com/wrbhljju";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Сообщаем Spring Boot приложению, куда слать сообщения
        registry.add("spring.rabbitmq.addresses", () -> CLOUD_AMQP_URL);
    }

    private Connection connection;
    private Channel channel;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() throws Exception {
        // 3. Подключаем сам ТЕСТ к облаку для проверки очереди
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(CLOUD_AMQP_URL);

        connection = factory.newConnection();
        channel = connection.createChannel();

        // Убедимся, что очередь существует в облаке
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);

        RestAssured.baseURI = "http://localhost:" + port;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null && channel.isOpen()) channel.close();
        if (connection != null && connection.isOpen()) connection.close();
    }

    @Test
    @DisplayName("End-to-End: API -> Cloud RabbitMQ Verification: raise TomCat, send message through TomCatš port, get message from the queue and check content")
    void testApiToRabbitFlow() throws Exception {
        Order order = new Order();
        order.setId(999);
        order.setStatus("ACCEPTED");

        // Act: Отправляем в наш локальный сервис (который перешлет в облако)
        RestAssured.given()
                .contentType("application/json")
                .body(order)
                .when()
                .post("/orders")
                .then()
                .statusCode(200);

        logger.info("Sent order to API and expected it in CloudAMQP");

        // Assert: Вычитываем из облака
        Awaitility.await()
                .atMost(Duration.ofSeconds(15)) // В облаке задержка чуть больше
                .untilAsserted(() -> {
                    GetResponse response = channel.basicGet(QUEUE_NAME, true);
                    Assertions.assertNotNull(response, "Cloud RabbitMQ queue is empty!");

                    Order received = mapper.readValue(response.getBody(), Order.class);
                    Assertions.assertEquals(order.getId(), received.getId());
                    logger.info("Successfully caught message from CloudAMQP!");
                });
    }
}