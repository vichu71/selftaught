package es.vicai.studylab.mail;

import es.vicai.studylab.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de correos transaccionales. Un fallo de envío nunca se propaga ni se registra con el
 * destinatario o el enlace: la API responde igual exista o no la cuenta.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties props;

    public MailService(ObjectProvider<JavaMailSender> sender, AppProperties props) {
        this.sender = sender;
        this.props = props;
    }

    public void sendVerification(String to, String token) {
        send(to, "Confirma tu correo en Study Lab",
                "Hola,\n\nPara activar tu cuenta abre este enlace (caduca en 24 horas):\n"
                        + props.baseUrl() + "/verify-email?token=" + token
                        + "\n\nSi no has sido tú, ignora este mensaje.");
    }

    public void sendAlreadyRegistered(String to) {
        send(to, "Ya tienes una cuenta en Study Lab",
                "Hola,\n\nAlguien ha intentado registrar este correo, pero ya tiene una cuenta.\n"
                        + "Si has sido tú, entra con tu contraseña o usa «He olvidado mi contraseña»:\n"
                        + props.baseUrl() + "/forgot-password\n\nSi no has sido tú, ignora este mensaje.");
    }

    public void sendPasswordReset(String to, String token) {
        send(to, "Restablece tu contraseña de Study Lab",
                "Hola,\n\nPara elegir una contraseña nueva abre este enlace (caduca en 1 hora):\n"
                        + props.baseUrl() + "/reset-password?token=" + token
                        + "\n\nSi no lo has pedido tú, ignora este mensaje: tu contraseña no cambia.");
    }

    private void send(String to, String subject, String text) {
        JavaMailSender mailSender = sender.getIfAvailable();
        if (!props.mail().enabled() || mailSender == null) {
            log.info("Correo desactivado: no se envía «{}»", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(props.mail().from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("No se pudo enviar «{}» ({})", subject, e.getClass().getSimpleName());
        }
    }
}
