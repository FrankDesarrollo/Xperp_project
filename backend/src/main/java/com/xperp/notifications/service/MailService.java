package com.xperp.notifications.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Correo hacia Mailpit (bandeja local, http://localhost:8025). Si el servidor de correo no está, se
 * registra en el log y la operación sigue: el correo nunca bloquea el flujo.
 */
@Service
class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);
  static final String FROM = "Xperp IA <ia@laceiba.demo>";

  private final JavaMailSender sender;

  MailService(JavaMailSender sender) {
    this.sender = sender;
  }

  void send(String to, String subject, String html, String attachmentName, byte[] attachment) {
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
      helper.setFrom(FROM);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(html, true);
      if (attachment != null) {
        helper.addAttachment(attachmentName, new ByteArrayResource(attachment), "application/pdf");
      }
      sender.send(message);
    } catch (Exception e) {
      log.warn("No se pudo enviar el correo a {}: {}", to, e.getMessage());
    }
  }
}
