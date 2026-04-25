package com.czertainly.core.messaging.configuration;

public class RabbitMQConstants {

    public static final String EXCHANGE_NAME = "czertainly";

    // Queues names
    public static final String QUEUE_EVENTS_NAME = "core.events";
    public static final String QUEUE_NOTIFICATIONS_NAME = "core.notifications";
    public static final String QUEUE_SCHEDULER_NAME = "core.scheduler";
    public static final String QUEUE_ACTIONS_NAME = "core.actions";
    public static final String QUEUE_VALIDATION_NAME = "core.validation";
    public static final String QUEUE_AUDIT_LOGS_NAME = "core.audit-logs";
    public static final String QUEUE_TIME_QUALITY_CONFIG_REQUEST = "time-quality.config-request";
    public static final String QUEUE_TIME_QUALITY_CONFIG = "time-quality.config";
    public static final String QUEUE_TIME_QUALITY_RESULTS = "time-quality.results";

    // routing keys
    public static final String EVENT_ROUTING_KEY = "event";
    public static final String NOTIFICATION_ROUTING_KEY = "notification";
    public static final String SCHEDULER_ROUTING_KEY = "scheduler";
    public static final String ACTION_ROUTING_KEY = "action";
    public static final String VALIDATION_ROUTING_KEY = "validation";
    public static final String AUDIT_LOGS_ROUTING_KEY = "audit-logs";
    public static final String TIME_QUALITY_CONFIG_REQUEST_ROUTING_KEY = "time-quality.config-request";
    public static final String TIME_QUALITY_CONFIG_ROUTING_KEY = "time-quality.config";
    public static final String TIME_QUALITY_RESULTS_ROUTING_KEY = "time-quality.results";

    private RabbitMQConstants() {
    }

}
