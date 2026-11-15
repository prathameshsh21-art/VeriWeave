package com.veriweave.common.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * Enterprise Production Email Service using Spring Boot JavaMailSender.
 * Supports TLS / STARTTLS (port 587) and SSL (port 465).
 * Fully compatible with AWS SES, SendGrid, Mailgun, Postmark, and Gmail SMTP.
 */
@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${veriweave.mail.from:no-reply@veriweave.com}")
    private String mailFrom;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private boolean starttlsEnable;

    @Value("${spring.mail.properties.mail.smtp.starttls.required:true}")
    private boolean starttlsRequired;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean smtpAuth;

    private JavaMailSender dynamicSender;

    /**
     * Checks whether an external SMTP email provider is configured.
     */
    public boolean isConfigured() {
        return StringUtils.hasText(mailHost) && !mailHost.trim().isEmpty();
    }

    private synchronized JavaMailSender getMailSender() {
        if (mailSender != null) {
            return mailSender;
        }
        if (dynamicSender != null) {
            return dynamicSender;
        }
        if (!isConfigured()) {
            return null;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailHost.trim());
        sender.setPort(mailPort);
        sender.setDefaultEncoding("UTF-8");

        if (StringUtils.hasText(mailUsername)) {
            sender.setUsername(mailUsername.trim());
        }
        if (StringUtils.hasText(mailPassword)) {
            sender.setPassword(mailPassword);
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(smtpAuth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttlsEnable));
        props.put("mail.smtp.starttls.required", String.valueOf(starttlsRequired));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        if (mailPort == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }

        this.dynamicSender = sender;
        return dynamicSender;
    }

    public void sendVerificationCode(String toEmail, String code) {
        if (!isConfigured()) {
            log.warn("Email verification code requested for {} but SMTP provider is not configured.", maskEmail(toEmail));
            throw new EmailDeliveryException(
                    "Email delivery provider is unconfigured (missing SPRING_MAIL_HOST). " +
                    "Email verification is required for new public registrations in production. " +
                    "For competition review, please use the pre-verified demo accounts."
            );
        }

        String subject = "VeriWeave — Verify Your Email Address";
        String body = """
                Welcome to VeriWeave.
                
                Your 6-digit verification code is: %s
                
                This code expires in 15 minutes.
                If you did not create a VeriWeave account, please ignore this email.
                """.formatted(code);

        deliver(toEmail, subject, body);
        log.info("Verification code successfully dispatched to {}", maskEmail(toEmail));
    }

    public void sendPasswordResetCode(String toEmail, String code) {
        if (!isConfigured()) {
            log.warn("Password reset email requested for {} but SMTP provider is not configured.", maskEmail(toEmail));
            throw new EmailDeliveryException(
                    "Email delivery provider is unconfigured (missing SPRING_MAIL_HOST). " +
                    "Password recovery requires active SMTP credentials. " +
                    "For competition review, use the configured demo accounts."
            );
        }

        String subject = "VeriWeave — Password Reset Verification Code";
        String body = """
                VeriWeave Password Recovery.
                
                Your 6-digit password reset code is: %s
                
                This code expires in 15 minutes.
                If you did not request a password reset, please secure your account immediately.
                """.formatted(code);

        deliver(toEmail, subject, body);
        log.info("Password reset code successfully dispatched to {}", maskEmail(toEmail));
    }

    public EmailDeliveryResult sendWorkspaceInvitation(String toEmail, String workspaceName, String role, String inviteUrl) {
        if (!isConfigured()) {
            log.warn("Workspace invitation email requested for {} in workspace '{}' but SMTP is not configured.",
                    maskEmail(toEmail), workspaceName);
            return new EmailDeliveryResult(
                    false,
                    "SMTP provider is unconfigured (missing SPRING_MAIL_HOST). Invitation link generated and ready for direct sharing."
            );
        }

        String subject = "You've been invited to join " + workspaceName + " on VeriWeave";
        String body = """
                You have been invited to join %s as a %s.
                
                Accept your invitation by visiting:
                %s
                
                This link expires in 7 days.
                """.formatted(workspaceName, role, inviteUrl);

        try {
            deliver(toEmail, subject, body);
            log.info("Workspace invitation email dispatched to {}", maskEmail(toEmail));
            return new EmailDeliveryResult(true, "Invitation email dispatched to " + toEmail);
        } catch (Exception ex) {
            log.error("Failed to deliver workspace invitation to {}: {}", maskEmail(toEmail), ex.getMessage());
            return new EmailDeliveryResult(
                    false,
                    "SMTP delivery failed: " + ex.getMessage() + ". Invitation link generated and ready for direct sharing."
            );
        }
    }

    private void deliver(String toEmail, String subject, String bodyText) {
        JavaMailSender sender = getMailSender();
        if (sender == null) {
            throw new EmailDeliveryException("SMTP sender could not be initialized. Verify SPRING_MAIL_HOST and credentials.");
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(toEmail);
            msg.setSubject(subject);
            msg.setText(bodyText);

            sender.send(msg);
        } catch (MailException ex) {
            throw new EmailDeliveryException("SMTP transport failure to " + maskEmail(toEmail) + ": " + ex.getMessage(), ex);
        }
    }

    public String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name.charAt(0) + "*@" + domain;
        }
        return name.substring(0, 2) + "***@" + domain;
    }
}
