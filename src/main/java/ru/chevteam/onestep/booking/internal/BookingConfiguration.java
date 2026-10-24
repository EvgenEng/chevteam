package ru.chevteam.onestep.booking.internal;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.chevteam.onestep.booking.BookingEvents;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class BookingConfiguration {

    @Bean
    NewTopic bookingEventsTopic() {
        return TopicBuilder.name(BookingEvents.TOPIC).partitions(3).replicas(1).build();
    }
}
