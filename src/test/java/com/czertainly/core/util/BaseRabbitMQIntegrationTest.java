package com.czertainly.core.util;

import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.List;
import java.util.Properties;

@ActiveProfiles("rabbitmq-integration")
public abstract class BaseRabbitMQIntegrationTest extends BaseSpringBootTest {

    private static final Properties TEST_PROPS;

    static {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        TEST_PROPS = yaml.getObject();
    }

    private static final String VHOST = TEST_PROPS.getProperty("test.rabbitmq.virtual-host");
    private static final String IMAGE = TEST_PROPS.getProperty("test.rabbitmq.image");

    static final RabbitMQContainer RABBIT = new RabbitMQContainer(IMAGE)
            .withVhost(VHOST);

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.virtual-host", () -> VHOST);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private List<Queue> declaredQueues;

    @BeforeEach
    void setUp() {
        // time-quality.config is declared by Time Quality Monitor; declare it here for testing purposes.
        Queue timeQualityConfigQueue = new Queue(RabbitMQConstants.QUEUE_TIME_QUALITY_CONFIG, true);
        rabbitAdmin.declareQueue(timeQualityConfigQueue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(timeQualityConfigQueue)
                .to(new DirectExchange(RabbitMQConstants.EXCHANGE_NAME))
                .with(RabbitMQConstants.TIME_QUALITY_CONFIG_ROUTING_KEY));

        declaredQueues.forEach(queue -> rabbitAdmin.purgeQueue(queue.getName(), false));
        rabbitAdmin.purgeQueue(RabbitMQConstants.QUEUE_TIME_QUALITY_CONFIG, false);
    }
}
