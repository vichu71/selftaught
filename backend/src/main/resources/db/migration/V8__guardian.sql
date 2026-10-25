-- F08: panel del adulto tutor. No hay tablas nuevas: el panel lee lo que guardaron F01 a F07 (ADR-59).
-- Solo hace falta un índice para leer de la auditoría los avisos de un alumno por actor (ADR-60); la tabla es de solo añadir y el índice no la modifica.
CREATE INDEX audit_event_actor_idx ON audit_event (actor_user_id, action, occurred_at);
