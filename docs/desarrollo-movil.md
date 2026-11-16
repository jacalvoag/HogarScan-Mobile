# Desarrollo e integración del móvil

## Organización

La aplicación conserva MVVM, organización por funcionalidad y
dependencias recibidas por constructor.

AppContainer construye las implementaciones y las conecta.

| Área | Responsable |
|---|---|
| Infraestructura HTTP y configuración | José |
| Autenticación y sesión | José |
| Propietarios, Room y migraciones | José |
| Persistencia y sincronización de resúmenes | José |
| Evaluaciones e identificación declarada | Uzzi |
| Presentación de hallazgos | Uzzi |
| Consulta y presentación de estadísticas | Uzzi |
| Navegación e integración general | José |

Cada responsable implementa las pruebas de su bloque.

Los cambios en dependencias, navegación, sesión, esquema local
o contratos compartidos se coordinan antes de implementarlos.

## Contrato de sesión

SessionReader expone un StateFlow de SessionState:

- Restoring: la restauración está pendiente.
- Guest: no hay una sesión autenticada.
- Authenticated: contiene un SessionSnapshot.

SessionSnapshot contiene contexto local, cuenta, correo,
vencimiento y generación de resúmenes. No contiene el token.

contextId es un identificador local distinto por cada nuevo login.
También debe cambiar si se sustituye la credencial activa.

Una actualización de summaryGeneration conserva el contexto
si continúa siendo la misma sesión.

Las funcionalidades capturan el contexto al iniciar una operación.
isCurrentSession permite comprobar si sigue activo y no ha vencido.

La comprobación no valida la generación del resumen ni sustituye
la autorización del servidor. Tampoco hace atómicas las escrituras:
la persistencia deberá comprobar el propietario y coordinarse con
los cambios de sesión.

El historial local existente no se asigna automáticamente a una cuenta.

## Integración pendiente

La configuración HTTPS, la construcción del cliente OkHttp y el
transporte compartido están disponibles en shared/data/http.

ApiTransport admite GET, POST y DELETE. Conserva el estado HTTP,
el cuerpo declarado como JSON y Retry-After cuando contiene segundos.

Distingue fallos de conexión, timeout, TLS y respuestas no admitidas.
La cancelación se propaga a la llamada HTTP.

El transporte limita las respuestas JSON a 1 MiB y no interpreta
sus campos. Los adaptadores deben validar el JSON y su estructura.

bearerToken es un detalle de la infraestructura de datos.
Las pantallas y los ViewModels no deben obtenerlo ni almacenarlo.

ApiErrorDecoder interpreta los errores HTTP del backend y conserva
estado HTTP, código, mensaje y Retry-After en segundos.

Las funcionalidades deciden por code, no por el texto de message.
Los códigos desconocidos se conservan para permitir una respuesta
general sin inventar acciones.

Las respuestas ausentes o inválidas producen un mensaje general y
code nulo. No deben utilizarse para cambiar generaciones.

Los conflictos STALE_SUMMARY_GENERATION e INVALID_SUMMARY_GENERATION
requieren una generación actual válida. Interpretar el error no
modifica datos ni provoca reintentos automáticos.

## Solicitudes autenticadas

Los repositorios de datos de funcionalidades autenticadas reciben
SessionApiClient y entregan el SessionSnapshot capturado para la operación.

Las solicitudes se construyen sin bearerToken. DefaultSessionApiClient
incorpora la credencial mediante SessionCredentialSource.

El cliente comprueba cuenta, contexto y vencimiento antes del envío
y al recibir el resultado.

SessionUnavailable indica que no hay una sesión utilizable.
SessionChanged indica que la operación pertenece a otro contexto:
su resultado no debe modificar la cuenta actual.

Una respuesta HTTP 401 con code UNAUTHENTICATED invalida únicamente
el contexto utilizado. Los errores de red y otros errores HTTP
no cierran automáticamente la sesión.

Cambiar summaryGeneration no equivale a cambiar de sesión.
Las reglas de generación se comprobarán en la sincronización.

La implementación de SessionCredentialSource debe hacer atómicas
la comprobación y lectura de credenciales, así como la comprobación
e invalidación del contexto.

Estas comprobaciones no sustituyen las validaciones del propietario
en persistencia ni revierten operaciones ejecutadas por el servidor.

## Registro de sesión activa

SessionRegistry mantiene en memoria el estado público y la credencial.
Las operaciones sobre credenciales se coordinan mediante un Mutex.

Cada activación crea un contexto local nuevo, incluso para la misma cuenta.
La invalidación exige coincidencia de cuenta y contexto.

completeRestoration solo aplica mientras el estado sea Restoring.
Devuelve true si aplicó el resultado, que puede ser Authenticated o Guest.
Una restauración tardía no reemplaza una sesión más reciente.

Las credenciales ausentes o vencidas completan la restauración como Guest.
tokenFor comprueba el vencimiento, pero no modifica por sí mismo el estado.

La generación puede actualizarse conservando contexto y token.
Las respuestas con una generación inferior a la actual se rechazan.
Los snapshots previamente capturados permanecen sin cambios.

Este registro mantiene la sesión durante la ejecución del proceso.
La persistencia protegida y su coordinación se incorporarán por separado.
La invalidación en memoria todavía no representa un logout remoto.

## Integración pendiente

AndroidSessionStorage proporciona persistencia cifrada de credenciales.
Quedan pendientes su coordinación con SessionRegistry, los flujos
de autenticación y la composición en AppContainer.

## Persistencia protegida de sesión

AndroidSessionStorage guarda las credenciales cifradas con AES-GCM.
La clave se genera y utiliza mediante Android Keystore.

El registro usa el formato interno HSC1 y se guarda dentro de
noBackupFilesDir, excluido de las operaciones de respaldo.
contextId no se persiste: se genera al activar la sesión en memoria.

Las operaciones utilizan Dispatchers.IO, un Mutex y AtomicFile.
La aplicación debe compartir una instancia del almacenamiento.

La lectura devuelve null cuando no existe un archivo.
Un archivo alterado, una clave ausente o un fallo de almacenamiento
produce SessionStorageException. Leer no genera una clave nueva.

clear elimina el archivo y la clave del almacenamiento.
Esta operación local no sustituye la revocación remota de la sesión.

## Flujo de trabajo

- Las ramas funcionales parten de develop.
- Los pull requests se dirigen a develop.
- main se reserva para la entrega estable.
- Cada rama admite varios commits pequeños y coherentes.
- Los cambios se verifican antes de integrarlos.
- Las pruebas simuladas se distinguen de las pruebas contra la API.