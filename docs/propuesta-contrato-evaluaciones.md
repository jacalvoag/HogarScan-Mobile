# Propuesta de integración de evaluaciones

Esta propuesta requiere acuerdo con José antes de integrarse como contrato
compartido. Los modelos iniciales viven en `evaluations/domain/model`; todavía
no están conectados a la navegación, persistencia ni API.

## Entrada y resultado

- La operación conserva localmente revisión, propietario e IP del objetivo.
- Cada solicitud contiene de una a seis observaciones de la misma IP, con
  puertos distintos de los permitidos. No se envían IP ni identificadores
  locales, duración, banners o evidencia de protocolo que no se obtuvo.
- La identificación opcional conserva fabricante, producto, versión y
  procedencia declarados por la persona usuaria.
- El resultado conserva la versión de reglas devuelta por la evaluación,
  fecha, estados de fuentes, hallazgos y coincidencias posibles. `in_kev`
  admite `null` y no se interpreta como `false`.
- El adaptador traduce `REJECTED` a `REFUSED` y `TIMED_OUT` a `TIMEOUT`.

## Decisiones por acordar

1. Operaciones y garantías del contrato de persistencia: propietario,
   revisión, objetivo, sustitución y respuestas tardías tras cambio de sesión.
2. Si se evalúan todos los objetivos de la revisión y cómo se representa un
   fallo o cancelación individual antes de declarar la revisión evaluada.
3. Cuándo congelar y encolar el resumen y cómo se reinicia la sincronización.
4. Parámetros, eventos y punto de entrada de la pantalla en la navegación.
5. Versión de backend a integrar y política de espera y errores para consultas
   que pueden depender de fuentes externas.

Las solicitudes autenticadas deberán capturar el contexto de sesión y usar
`SessionApiClient`. Comprobar la sesión no hace atómica la persistencia: la
implementación compartida debe proteger también la escritura por propietario.

## Componentes aislados de presentación

`EvaluationRoute` conecta `EvaluationScreen` con `EvaluationViewModel`.
El ViewModel recibe la revisión, propietario, dirección y observaciones del
objetivo, junto con `SessionReader` y `EvaluationGateway`. Comprueba la sesión
al iniciar y antes de publicar el resultado, descarta respuestas tardías al
cancelar y limpia la presentación cuando cambia la sesión. No persiste ni
encola el resultado. `EvaluationResultPanel` puede usarse por separado.
El integrador debe crear el ViewModel con el ciclo de vida adecuado y conservar
la asociación local; la escritura futura exige comprobar el propietario de
forma atómica en la capa de persistencia.

La conversión `toEvaluationRequest` prepara únicamente observaciones e
identificación. `EvaluationRequestJson` serializa esos datos sin incorporar
contexto local. `HttpEvaluationDataSource` envía la solicitud con la sesión
capturada y comprueba el propietario; `EvaluationResponseJson` interpreta los
resultados y las fuentes. `DefaultEvaluationGateway` adapta los resultados al
contrato del dominio. Los nombres `REFUSED` y `TIMEOUT` se traducen antes
del envío. Estas piezas están aisladas, no conectadas a `AppContainer`, y aún
requieren pruebas con Gradle y contra la versión acordada del backend.

`HttpRulesDataSource` consulta `GET /rules` sin credenciales y transforma los
metadatos publicados a `EvaluationRules`. La versión procede del servidor;
la aplicación no utiliza estas reglas para ejecutar otro motor de evaluación.

## Preparación del detalle de hallazgo

`EvaluationResult.findingDetail(index)` transforma un hallazgo de la respuesta
en datos de presentación: prioridad, categoría, certeza, evidencia, explicación,
recomendación, limitación, identificador y versión de regla, y estados NVD/KEV.
Una coincidencia CVE solo se asocia con su identificador correspondiente; KEV
desconocido no se muestra como negativo. El índice se utiliza únicamente dentro
de esa respuesta y no es identificador permanente.

La vista de detalle del Figma contiene iconos SVG y fuentes que el proyecto no
tiene configurados. Su integración visual y navegación siguen pendientes de
acordar con José, ya que el soporte de recursos o dependencias puede afectar
archivos compartidos. El texto de ejemplo del diseño no debe utilizarse como
evidencia real: el escaneo TCP actual no identifica por sí solo HTTP ni produce
porcentajes de certeza o vulnerabilidades confirmadas.
