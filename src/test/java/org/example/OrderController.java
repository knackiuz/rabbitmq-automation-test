package org.example;

import models.Order;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final RabbitTemplate rabbitTemplate;
    public static final String QUEUE_NAME = "automation_queue";

    public OrderController(RabbitTemplate rabbitTemplate){
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    public String createOrder(@RequestBody Order order){
        rabbitTemplate.convertAndSend(QUEUE_NAME, order);
        return "Order sent to RabbitMQ";
    }
}
