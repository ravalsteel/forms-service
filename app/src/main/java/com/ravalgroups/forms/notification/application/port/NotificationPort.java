package com.ravalgroups.forms.notification.application.port;

public interface NotificationPort {

    void sendEmail(String recipient, String subject, String body, String eventType);
}
