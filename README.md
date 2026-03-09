# RabbitMQ Automation Testing Project

This project contains a suite of automated tests for validating message flows using Spring Boot and the RabbitMQ Native Java Client, connected to a CloudAMQP instance.

## 🚀 Technology Stack
* **Java 17**
* **Spring Boot 3.2.3**
* **RabbitMQ** (CloudAMQP)
* **JUnit 5 & AssertJ**
* **RestAssured** (API Testing)
* **Awaitility** (Asynchronous Validation)
* **Jackson** (JSON Serialization)

## 🧪 Test Suites

### 1. RabbitIntegrationTest (Spring Boot)
Validates the **End-to-End flow** from a REST API to RabbitMQ:
* **API -> Producer**: Sends a POST request to `/orders`.
* **Broker**: Message is routed via CloudAMQP.
* **Consumer**: The test verifies the message arrives in the queue with correct data.

### 2. RabbitStatusTest (Native Client)
Validates **low-level RabbitMQ operations** using the `com.rabbitmq.client` library:
* **Manual Connection**: Establishes connection using `ConnectionFactory` and `Channel`.
* **Queue Management**: Declares and purges queues (`queueDeclare`, `queuePurge`).
* **Message Lifecycle**:
    * `testSimplePublishAndReceive`: Basic string message publishing and consumption.
    * `testJsonMessageProcessing`: Complex JSON object serialization/deserialization.
* **Manual Acknowledgement**: Demonstrates the `basicAck` mechanism to confirm message processing only after successful assertions.


## 🛠 Setup and Configuration

### CloudAMQP Credentials
The tests connect to a cloud instance. The connection string used is:
`amqps://wrbhljju:***@cow.rmq2.cloudamqp.com/wrbhljju`

### Important Configurations
To support JSON payloads in Spring Boot, the `Jackson2JsonMessageConverter` bean is defined in `MyApplication.java`:

```java
@Bean
public Jackson2JsonMessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}