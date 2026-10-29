# F19 · FASE 4 — tests que fallan antes del código

> 29-10-2026. Entorno cloud sin Docker ni PostgreSQL (ver `cloud-env-check.md`): el frontend se ejecutó aquí; el backend **solo se compiló**.

## Resultado

| Parte | Ficheros nuevos | Estado |
|-------|-----------------|--------|
| Frontend (Vitest) | `api/photos`, `material/DropZonePhotos`, `components/MaterialListPhotos`, `stores/materialPhotos`, `views/GuardianPhotoPermission`, `results/taskLabelsF19` | **37 fallan** y 9 pasan (los que describen lo que no cambia); los 1127 tests previos siguen en verde |
| Backend: puros (sin BD) | `material/image/PhotoProcessorTest`, `material/image/ImageReadParserTest` | no compilan: faltan `PhotoProcessor`, `PhotoRejectedException` e `ImageReadParser` |
| Backend: con BD | `PhotoUploadTest`, `PhotoRateLimitTest`, `PhotoProcessingTest`, `PhotoMinorTest`, `PhotosDisabledTest`, `MaterialCapabilitiesTest`, `PhotoSchemaTest`, `PhotoArchitectureTest`, `ai/VisionGatewayTest`, `ai/OcrStartupSettingsTest`, `ai/provider/VisionProvidersTest`, `childsettings/ChildSettingsTest`, `childsettings/ChildSettingsRealCookiesTest` | no compilan (`mvn test-compile` falla solo por símbolos que debe crear la FASE 6) y **no se han podido ejecutar** |
| Apoyo | `material/PhotoFixtures`, `material/PhotoTestBase`, `application-test.properties` (tarea `ocr` ficticia) | listos |

## Qué falta por comprobar en tu máquina
- Que el backend **compila sin más errores** que los símbolos nuevos (javac se detiene en la resolución de símbolos y puede ocultar otros fallos de los tests): `docker compose … run --rm --build backend-test`.
- Que cada test de backend **falla por la razón buena** (no por un error del propio test) antes de empezar la FASE 6.
- Los nombres de las restricciones que el test de esquema da por supuestos (`source_document_media_type_check`) y las columnas de `guardianship` (`tutor_id`, `student_id`).

## Cobertura por criterio
AC-1, 2, 3 (`PhotoUploadTest`, `PhotoProcessorTest`, `PhotoProcessingTest`) · AC-4, 5 (`VisionGatewayTest`, `VisionProvidersTest`, `OcrStartupSettingsTest`, `PhotosDisabledTest`) · AC-6, 7, 8, 9 (`PhotoProcessingTest`, `ImageReadParserTest`, `PhotoArchitectureTest`) · AC-10 (`PhotoRateLimitTest`) · AC-11, 11b (`PhotoMinorTest`, `ChildSettingsTest`, `ChildSettingsRealCookiesTest`) · AC-12, 13 (`PhotoUploadTest`, `PhotoProcessingTest`, `PhotoArchitectureTest`) · AC-14 (frontend) · AC-15 (`PhotoProcessingTest` y la autoselección de F18, que no mira el tipo) · AC-16 (suites existentes).

No hay test de la vista del asistente «Nuevo curso» con fotos: la autoselección (`autoSelect`) no mira el tipo del documento y ya está probada; se revisará en la pasada del navegador.
