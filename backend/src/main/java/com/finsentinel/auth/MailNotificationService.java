package com.finsentinel.auth;

import com.finsentinel.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationService.class);
    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public MailNotificationService(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    public boolean sendVerificationEmail(String recipientEmail, String fullName, String rawToken) {
        String link = appProperties.baseUrl() + "/auth/verify-email?token=" + rawToken;
        String subject = "Verify your FinSentinel AI email address";
        String body = "Hello " + fullName + ",\n\n"
                + "Please verify your email address for FinSentinel AI by opening the single-use link below (valid for 24 hours):\n"
                + link + "\n\n"
                + "If you did not create this account, you can safely ignore this message.\n";
        return send(recipientEmail, subject, body);
    }

    public boolean sendPasswordResetEmail(String recipientEmail, String fullName, String rawToken) {
        String link = appProperties.baseUrl() + "/auth/reset-password?token=" + rawToken;
        String subject = "Reset your FinSentinel AI password";
        String body = "Hello " + fullName + ",\n\n"
                + "A password reset was requested for your FinSentinel AI account. Use this single-use link within 60 minutes:\n"
                + link + "\n\n"
                + "If you did not request this reset, no changes were made.\n";
        return send(recipientEmail, subject, body);
    }

    public boolean sendReminderEmail(String recipientEmail, String subject, String body) {
        return send(recipientEmail, subject, body);
    }

    private boolean send(String to, String subject, String body) {
        if (!appProperties.mail().enabled()) {
            log.info("Mail delivery disabled by configuration; skipping email subject='{}'", subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(appProperties.mail().from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Email dispatch failed for subject='{}': {}", subject, ex.getMessage());
            return false;
        }
    }
}
