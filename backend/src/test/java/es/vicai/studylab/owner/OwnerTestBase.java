package es.vicai.studylab.owner;

import java.util.List;
import org.springframework.test.context.TestPropertySource;

/**
 * Base de los tests de F14 con propietario. Contrato (ART-003): todo cuelga de {@code /api/owner/**}; el propietario es la cuenta adulta activa cuyo correo
 * coincide con {@code app.owner.email} (aquí, Ana, escrito con mayúsculas y espacios para comprobar que se normaliza). Quien tiene sesión y no es
 * propietario recibe el mismo 404 que ante una ruta inexistente. Sin variable, nadie es propietario ({@link OwnerDisabledTest}).
 * Topes de la configuración de test: menor 1,00 USD, adulto 5,00 USD, global 10,00 USD.
 */
@TestPropertySource(properties = {
        "app.owner.email=  ANA@Example.com ",
        "app.ai.pricing[fake/fake-1].verified-on=2020-01-01",
        "app.ai.pricing[fake/fake-undated].input-per-million=3.00",
        "app.ai.pricing[fake/fake-undated].output-per-million=4.00"})
public abstract class OwnerTestBase extends OwnerFamilyBase {

    protected static final String NOT_FOUND_PROBE = "/api/zzz-no-existe";

    /** Los cinco informes de solo lectura (sin la lista de usuarios). */
    protected static final List<String> REPORTS = List.of("/api/owner/ai", "/api/owner/prices", "/api/owner/accounts", "/api/owner/usage", "/api/owner/health");
}
