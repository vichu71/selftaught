package es.vicai.studylab.tutor;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros del tutor (F07): valores de ingeniería sin variable de entorno. Mensajes por hora y por día de cada usuario, mensajes que se conservan por lección,
 * cuánto historial se envía al modelo y cuánto tiempo bloquea una respuesta pendiente a la siguiente (ADR-55, 56). Los límites se cuentan sobre los mensajes
 * conservados: {@code maxStored} no debe bajar de {@code 2 × maxPerDay} o la poda de una lección dejaría el límite del día sin efecto.
 */
@ConfigurationProperties(prefix = "app.tutor")
public record TutorProperties(
        @DefaultValue("30") int maxPerHour,
        @DefaultValue("150") int maxPerDay,
        @DefaultValue("400") int maxStored,
        @DefaultValue("6") int historyExchanges,
        @DefaultValue("6000") int historyChars,
        @DefaultValue("2m") Duration busyWindow) {}
