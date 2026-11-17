# Propuesta de integración de evaluaciones

Esta propuesta documenta el flujo integrado en la rama de evaluaciones para
revisión conjunta con José. Los resultados se guardan localmente, se encola un
resumen y se solicita sincronización. Las pruebas de Room y migración se han
ejecutado en un emulador; siguen pendientes las pruebas con la API autenticada
y los teléfonos físicos.

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

## Puntos para revisar en conjunto

1. Confirmar con José el contrato de persistencia y las garantías frente a
   cambios de sesión o generación.
2. La implementación actual evalúa todos los objetivos en secuencia y descarta
   el lote parcial si alguno falla; confirmar la política de recuperación.
3. Revisar los puntos de integración del historial, sincronización y borrado.
4. Validar la API autenticada, sus tiempos de espera y las fuentes externas.

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
del envío. El flujo de revisión completa está conectado desde el historial;
todavía requiere pruebas contra la API autenticada y en teléfonos físicos.

`HttpRulesDataSource` consulta `GET /rules` sin credenciales y transforma los
metadatos publicados a `EvaluationRules`. La versión procede del servidor;
la aplicación no utiliza estas reglas para ejecutar otro motor de evaluación.
Desde el detalle de revisión se pueden consultar esos metadatos, declarar una
identificación por objetivo, evaluar y volver a intentar la sincronización.

## Evaluación completa y preparación del resumen

`EvaluateCompletedScan` recibe una revisión completa, sesión y, opcionalmente,
identificaciones por objetivo. Evalúa un objetivo por solicitud en secuencia,
con progreso y propagación de cancelación. Ante fallo devuelve el objetivo y el
tipo de error sin declarar completada la evaluación. Ante cambio de cuenta,
contexto o generación descarta el resultado. No guarda resultados parciales ni
hace reintentos; la política de recuperación y persistencia queda por acordar.

`prepareEvaluatedScanSummary` requiere exactamente una evaluación por objetivo
con las observaciones, revisión y propietario correspondientes. Calcula las
direcciones que respondieron y los conteos de hallazgos, conservando la
generación capturada al inicio de la revisión. `RoomEvaluationStore` guarda las
evaluaciones y encola el resumen dentro de una transacción, mientras el almacén
de sesión impide cambiar cuenta o generación durante esa operación local. No se
mantiene ese bloqueo durante llamadas de red. Si falla la sincronización, los
datos y el resumen pendiente permanecen disponibles para reintentar.
Una prueba instrumentada ejecuta el flujo con Room y transporte simulado: tras
un fallo de red, recupera la evaluación y reintenta el mismo resumen sin IP.

El borrado de resúmenes del servidor requiere confirmación en la pantalla de
cuenta y conserva el historial local. La petición utiliza la generación que se
mostró al confirmar; nunca se repite automáticamente con una nueva generación.
Una respuesta perdida se presenta como resultado no confirmado y exige que la
persona compruebe el estado antes de volver a tomar una decisión.

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
