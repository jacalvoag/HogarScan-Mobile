# Propuesta de integración de evaluaciones

El historial conecta evaluación autenticada, guardado local atómico, resumen
pendiente y sincronización. Las comprobaciones automatizadas con transporte
simulado se distinguen de las pruebas contra la API real y en teléfonos físicos.

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

## Recuperación e integración

La política implementada evalúa todos los objetivos en secuencia. Si alguno
falla, no declara completo ni guarda el lote temporal de evaluaciones. El detalle
TCP de la revisión permanece en Room; un reintento explícito vuelve a evaluar
el lote completo. No hay reintentos automáticos ni resúmenes parciales.

El formulario integrado y las identificaciones por objetivo pertenecen al
ViewModel de cada revisión: sobreviven a la rotación y a los cambios de pestaña
mientras se conserve ese ViewModel. Se limpian al cambiar cuenta, contexto o
generación. No se promete restauración del formulario tras terminar el proceso.

Presentación utiliza `ScanEvaluationActions`, un contrato de dominio. La capa
de datos adapta el flujo compartido, Room, los resultados de sincronización y
la consulta pública de reglas. Compose no ejecuta solicitudes HTTP.

La pantalla consulta el estado persistido de su resumen al cargar y al terminar
un intento de sincronización. Distingue pendiente, sincronizado, conflicto y
obsoleto; terminar un lote no equivale a confirmar todos sus resúmenes. Solo
ofrece reintento para pendientes. Ante una generación futura solicita cerrar e
iniciar sesión de nuevo, sin cambiar el resumen ni reenviarlo automáticamente.
Actualizar el estado en el mismo ViewModel no elimina esa indicación.

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
hace reintentos automáticos; se aplica la política de recuperación descrita arriba.

`prepareEvaluatedScanSummary` requiere exactamente una evaluación por objetivo
con las observaciones, revisión y propietario correspondientes. Calcula las
direcciones que respondieron y los conteos de hallazgos, conservando la
generación capturada al inicio de la revisión. `RoomEvaluationStore` guarda las
evaluaciones y encola el resumen dentro de una transacción, mientras el almacén
de sesión impide cambiar cuenta o generación durante esa operación local. No se
mantiene ese bloqueo durante llamadas de red. Si falla la sincronización, los
datos y el resumen pendiente permanecen disponibles para reintentar.
La preparación JSON se ejecuta en un dispatcher de trabajo antes de adquirir
el bloqueo de sesión; la escritura vuelve a comprobar propietario y generación.
Los resultados grandes se muestran en una lista diferida de altura acotada.
Una prueba instrumentada ejecuta el flujo con Room y transporte simulado: tras
un fallo de red, recupera la evaluación y reintenta el mismo resumen sin IP.

El borrado de resúmenes del servidor requiere confirmación en la pantalla de
cuenta y conserva el historial local. `DeleteSummariesViewModel` mantiene la
confirmación y el envío pendiente al recrear la pantalla, evita envíos duplicados
y descarta respuestas de otras cuentas. La petición utiliza la generación que se
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

La vista de detalle se abre desde cada hallazgo de la revisión y utiliza los
iconos SVG originales del diseño. Presenta únicamente los datos y estados de
fuentes disponibles en el resultado; sus enlaces NVD se validan antes de abrirse.
El texto de ejemplo del diseño no se utiliza como evidencia real: el escaneo
TCP actual no identifica por sí solo HTTP ni produce porcentajes de certeza o
vulnerabilidades confirmadas. Las fuentes tipográficas del diseño y la revisión
visual de las demás pantallas permanecen pendientes.
