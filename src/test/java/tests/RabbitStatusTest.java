package tests;

import models.Order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RabbitStatusTest {
    private static final Logger logger = LoggerFactory.getLogger(RabbitStatusTest.class);

    private static final String AMQP_URL = "amqps://wrbhljju:Gt3nhDUT4t9sshJbhbj3jPeKvsbdnOgN@cow.rmq2.cloudamqp.com/wrbhljju";
    private static final String QUEUE_NAME = "automation_queue";

    private Connection connection;
    private Channel channel;

    @BeforeEach
    void setup() throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(AMQP_URL);
        connection = factory.newConnection();
        channel = connection.createChannel();

        // Creating the queue if it does not exist
        // b - durable: true (If RabbitMQ server restarts, the queue will persist.)
        // b1 - exclusive: false (If set to true, the queue is deleted when the connection is closed.)
        // b2 - autoDelete: false (If set to true, the queue is deleted once the last consumer disconnects. We set it to false to ensure the queue persists between tests.)
        // map - arguments: null (Additional settings: messages lifetime or limits)
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        channel.queuePurge(QUEUE_NAME);
    }

    @AfterEach
    void tearDown() throws Exception{
        if(channel != null){
            channel.close();
        }

        if(connection != null){
            connection.close();
        }
    }

    @Test
    @DisplayName("Test: send message to queue, getting message from the queue, check that body is same and delete the message")
    void testSimplePublishAndReceive() throws Exception{
        String message = "Status: PROCESSED";

        // Publish a test message
        // s - exchange: "" (Exchange name. If empty - default direct Exchange)
        // if Exchange is empty, routingKey should be same as queue)
        // basicProperties: null (metadata)
        // body (Codding the message to bytes - RabbitMQ protocol works with bytes)
        channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
        //System.out.println("Sent: " + message);
        logger.info("Sent: {}", message);

        // Waiting for the message to appear and checking it
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    GetResponse response = channel.basicGet(QUEUE_NAME, true); //b: true - delete message for the queue when processed
                    Assertions.assertNotNull(response, "Queue is empty!");
                    String body = new String(response.getBody());
                    Assertions.assertEquals(message, body);
                });
    }

    @Test
    @DisplayName("Test: send message with status 'IN_PROGRESS' to queue, send same message with changed status 'PROCESSED' to the queue, " +
            "getting messages from the queue, check and delete the messages by tag")
    void testJsonMessageProcessing() throws Exception{
        ObjectMapper mapper = new ObjectMapper();

        // Test data
        Order order = new Order();
        order.setId(12345);
        order.setStatus("IN_PROGRESS");

        List<Long> tags = new ArrayList<>();

        byte[] jsonMessage = mapper.writeValueAsBytes(order);

        channel.basicPublish("", QUEUE_NAME, null, jsonMessage);
        logger.info("Set JSON message with status: {} to queue", order.getStatus());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    GetResponse response = channel.basicGet(QUEUE_NAME, false);
                    Assertions.assertNotNull(response);

                    Order receivedOrder = mapper.readValue(response.getBody(), Order.class);
                    Assertions.assertEquals(order.getId(), receivedOrder.getId());
                    Assertions.assertEquals(order.getStatus(), receivedOrder.getStatus());

                    tags.add(response.getEnvelope().getDeliveryTag());
                });

        // Change status of the order
        order.setStatus("PROCESSED");
        byte[] updatedJsonMessage = mapper.writeValueAsBytes(order);

        channel.basicPublish("", QUEUE_NAME, null, updatedJsonMessage);
        logger.info("Set JSON message with status: {} to queue", order.getStatus());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    GetResponse response = channel.basicGet(QUEUE_NAME, false);
                    Assertions.assertNotNull(response);

                    Order receivedOrder = mapper.readValue(response.getBody(), Order.class);
                    Assertions.assertEquals(order.getId(), receivedOrder.getId());
                    Assertions.assertEquals(order.getStatus(), receivedOrder.getStatus());

                    tags.add(response.getEnvelope().getDeliveryTag());
                });


        // Delete messages from the server only after a successful test
        for (Long tag: tags){
            channel.basicAck(tag, false);
            logger.info("Message with tag: {} verified and acknowledged.", tag);
        }
    }

    @Test
    @DisplayName("Test: verify custom headers and message properties")
    void testMessagePropertiesAndHeaders() throws Exception{
        String message = "Header test";
        String correlationId = "test-123-abc";

        // Create custom properties with correlation ID
        AMQP.BasicProperties properties = new AMQP.BasicProperties().builder().correlationId(correlationId).contentType("text/plain").build();

        channel.basicPublish("", QUEUE_NAME, properties, message.getBytes());
        logger.info("Sent message with correlation ID {}", correlationId);

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    GetResponse response = channel.basicGet(QUEUE_NAME, true);
                    Assertions.assertNotNull(response);

                    // Verify that metadata (correlation ID) is correct
                    Assertions.assertEquals(correlationId, response.getProps().getCorrelationId());
                    logger.info("Correlation ID verified successfully");
                });
    }

    @Test
    @DisplayName("Test: verify message requeue after negative acknowledgement")
    void testMessageRequeue() throws Exception{
        String message = "Requeue me";

        channel.basicPublish("", QUEUE_NAME, null, message.getBytes());

        // Get message and reject it with requeue = true
        GetResponse firstResponse = channel.basicGet(QUEUE_NAME, false); // autoAck: false
        long tag = firstResponse.getEnvelope().getDeliveryTag();

        // basicNack: tag, multiple: false, requeue: true
        channel.basicNack(tag, false, true);
        logger.info("Message with tag {} rejected and send back to queue", tag);

        // Verify the message is back in the queue
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    GetResponse secondResponse = channel.basicGet(QUEUE_NAME, true);
                    Assertions.assertNotNull(secondResponse, "Message should be back in the queue!");
                    Assertions.assertEquals(message, new String(secondResponse.getBody()));
                    logger.info("Message successfully recovered from the queue");
                });
    }
}
