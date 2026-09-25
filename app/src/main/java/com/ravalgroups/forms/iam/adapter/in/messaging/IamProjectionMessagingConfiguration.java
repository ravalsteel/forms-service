package com.ravalgroups.forms.iam.adapter.in.messaging;

import com.ravalgroups.forms.shared.config.FormsMessagingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares broker-service topology conventions for IAM → Forms projection.
 */
@Configuration(proxyBeanMethods = false)
public class IamProjectionMessagingConfiguration {

    @Bean
    TopicExchange domainEventsExchange(FormsMessagingProperties properties) {
        return new TopicExchange(properties.domainEventsExchange(), true, false);
    }

    @Bean
    Queue iamProjectionQueue(FormsMessagingProperties properties) {
        return QueueBuilder.durable(properties.iamProjectionQueue()).build();
    }

    @Bean
    Binding iamUserCreatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.user.created");
    }

    @Bean
    Binding iamUserUpdatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.user.updated");
    }

    @Bean
    Binding iamUserDeactivatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.user.deactivated");
    }

    @Bean
    Binding iamMembershipCreatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.membership.created");
    }

    @Bean
    Binding iamMembershipUpdatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.membership.updated");
    }

    @Bean
    Binding iamMembershipDeactivatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.membership.deactivated");
    }

    @Bean
    Binding iamCompanyCreatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.company.created");
    }

    @Bean
    Binding iamCompanyUpdatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.company.updated");
    }

    @Bean
    Binding iamDepartmentCreatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.department.created");
    }

    @Bean
    Binding iamDepartmentUpdatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.department.updated");
    }

    @Bean
    Binding iamDepartmentDeactivatedBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.department.deactivated");
    }

    @Bean
    Binding iamApplicationAccessBinding(Queue iamProjectionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(iamProjectionQueue).to(domainEventsExchange).with("iam.application_access.*");
    }
}
