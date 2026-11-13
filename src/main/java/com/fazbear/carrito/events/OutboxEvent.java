package com.fazbear.carrito.events;

import jakarta.persistence.*;

@Entity
@Table(name = "ev3_outbox", indexes = @Index(name = "ev3_outbox_pending_idx", columnList = "delivered,id"))
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false) String topic;
    @Column(nullable = false) String eventKey;
    @Column(columnDefinition = "text") String payload;
    boolean delivered;
    protected OutboxEvent() { }
    OutboxEvent(String topic, String key) { this.topic = topic; this.eventKey = key; }
}
