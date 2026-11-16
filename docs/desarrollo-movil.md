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

La configuración del destino HTTPS y la construcción del cliente
OkHttp están disponibles en shared/data/http.

Quedan pendientes la ejecución compartida de solicitudes, sus errores,
la implementación de SessionReader, el almacenamiento de credenciales
y su conexión a AppContainer.

## Flujo de trabajo

- Las ramas funcionales parten de develop.
- Los pull requests se dirigen a develop.
- main se reserva para la entrega estable.
- Cada rama admite varios commits pequeños y coherentes.
- Los cambios se verifican antes de integrarlos.
- Las pruebas simuladas se distinguen de las pruebas contra la API.