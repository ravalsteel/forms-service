package com.ravalgroups.forms.notification.adapter.out;

import com.ravalgroups.forms.notification.adapter.out.persistence.NotificationLogEntity;
import com.ravalgroups.forms.notification.adapter.out.persistence.NotificationLogJpaRepository;
import com.ravalgroups.forms.notification.application.port.NotificationPort;
import com.ravalgroups.forms.shared.config.FormsNotificationProperties;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "forms.notification.email.provider", havingValue = "smtp")
public class SmtpNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final NotificationLogJpaRepository logs;
    private final FormsNotificationProperties properties;

    public SmtpNotificationAdapter(
            JavaMailSender mailSender,
            NotificationLogJpaRepository logs,
            FormsNotificationProperties properties) {
        this.mailSender = mailSender;
        this.logs = logs;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void sendEmail(String recipient, String subject, String body, String eventType) {
        if (recipient == null || recipient.isBlank()) {
            logs.save(NotificationLogEntity.create(
                    UUID.randomUUID(),
                    null,
                    "EMAIL",
                    "(missing)",
                    subject,
                    eventType,
                    "SKIPPED",
                    "No recipient",
                    Instant.now()));
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(recipient);
            helper.setFrom(properties.from(), properties.fromName() == null ? "Forms" : properties.fromName());
            helper.setSubject(subject == null ? "Forms notification" : subject);
            helper.setText(body == null ? "" : body, false);
            mailSender.send(message);
            logs.save(NotificationLogEntity.create(
                    UUID.randomUUID(),
                    null,
                    "EMAIL",
                    recipient,
                    subject,
                    eventType,
                    "SENT",
                    null,
                    Instant.now()));
        } catch (Exception ex) {
            log.warn("notification.email SMTP failed eventType={} recipient={}: {}", eventType, recipient, ex.getMessage());
            logs.save(NotificationLogEntity.create(
                    UUID.randomUUID(),
                    null,
                    "EMAIL",
                    recipient,
                    subject,
                    eventType,
                    "FAILED",
                    truncate(ex.getMessage()),
                    Instant.now()));
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "SMTP send failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
