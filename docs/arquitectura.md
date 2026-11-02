# Arquitectura inicial de HogarScan

## 1. Alcance

Este repositorio contiene el frontend Android de HogarScan,
desarrollado con Kotlin y Jetpack Compose.

La aplicación ejecuta revisiones TCP acotadas en la red doméstica.
Room conservará el detalle local. Una API NestJS interpretará las
observaciones y almacenará resúmenes estadísticos en PostgreSQL.

El nombre actual del repositorio se conserva temporalmente.

## 2. Modelo de arquitectura

Se utiliza MVVM para la presentación y separación de responsabilidades
entre presentación, dominio y datos.

El proyecto comienza con un único módulo Gradle: app.
La organización interna se realiza por funcionalidad.

### Presentación

- Pantallas y componentes Compose.
- ViewModels y estados de interfaz.
- Recepción de acciones y presentación de resultados.
- Las pantallas no ejecutan sockets ni acceden directamente a Room o HTTP.

### Dominio

- Modelos y reglas del proyecto.
- Validación del alcance de una revisión.
- Casos de uso que coordinan operaciones con reglas relevantes.
- Contratos para acceder a capacidades externas.
- Independiente de Context, Activity, Compose, Room y clientes HTTP.

### Datos

- Implementaciones de los contratos del dominio.
- Detección de red y conexiones TCP mediante Android.
- Persistencia con Room y comunicación con la API.
- Conversión entre modelos de dominio, entidades y cuerpos JSON.

## 3. Dependencias

- Presentación depende de dominio.
- Datos depende de los contratos y modelos de dominio.
- Dominio no depende de presentación ni de datos.
- La composición de la aplicación construye y conecta las implementaciones.

Las dependencias se reciben por constructor. Inicialmente se construirán
manualmente desde un AppContainer y las fábricas necesarias.

Se crearán interfaces en los límites con Android, almacenamiento y API.
Los casos de uso se incorporarán cuando agrupen reglas o coordinen
operaciones que justifiquen su existencia.

## 4. Organización

Dentro de com.example.hogarscan:

| Paquete | Responsabilidad |
|---|---|
| app | Construcción de dependencias y navegación general. |
| scan/presentation | Pantallas, ViewModels y estados de revisión. |
| scan/domain | Modelos, reglas, casos de uso y contratos de revisión. |
| scan/data | Adaptadores Android y, después, persistencia y API. |
| ui/theme | Tema visual existente. |

Autenticación y estadísticas incorporarán sus propios paquetes cuando
comience su implementación. Las carpetas se crean al añadir código real.

El historial de revisiones pertenece inicialmente a la funcionalidad scan.

## 5. Modelos propuestos

| Modelo | Responsabilidad y datos principales |
|---|---|
| NetworkScope | IPv4 del teléfono, prefijo y subred detectada. |
| ScanConfig | Rango y puertos autorizados para una revisión. |
| ScanPolicy | Límites internos de direcciones, puertos, concurrencia y tiempo. |
| ScanRun | Identificador, alcance, configuración, fechas y estados de la revisión. |
| ProbeResult | IP, puerto, resultado TCP y duración de un intento. |
| Finding | Interpretación de la API: categoría, evidencia, certeza y recomendación. |
| ScanSummary | Identificador, fecha y conteos congelados para sincronización. |
| ScanUiState | Estado de presentación que consume la pantalla. |

La conexión Android utilizada para ejecutar la revisión permanece en
el adaptador de datos. Su cambio debe invalidar la autorización.

ScanRun distingue tres aspectos independientes:
- Ejecución local.
- Evaluación de observaciones.
- Sincronización del resumen.

Los resultados TCP distinguen aceptación, rechazo explícito,
tiempo de espera y error.

La lista de direcciones con respuesta se calcula por revisión.
Una IP no se utiliza como identificador permanente de un dispositivo.

## 6. Reglas que condicionan la implementación

- Calcular la subred usando la IPv4 y el prefijo reales.
- Mostrar el alcance y solicitar autorización antes de ejecutar.
- Invalidar la autorización al modificar el alcance o cambiar la red.
- Detener la revisión si se pierde o cambia la conexión autorizada.
- Conservar los resultados parciales al interrumpir una revisión.
- Un timeout no confirma ausencia de un equipo.
- Una conexión aceptada no demuestra una vulnerabilidad.
- Separar observaciones TCP de interpretaciones y hallazgos.
- Sincronizar únicamente resúmenes de revisiones completas y evaluadas.
- Reintentar la sincronización sin duplicar el resumen.
- Mantener el detalle local separado por cuenta.
- Conservar IP privadas y pruebas individuales en el teléfono.

## 7. Desarrollo incremental

Cada cambio debe tener:
1. Un objetivo concreto.
2. Archivos y responsabilidades identificados.
3. Una forma de comprobar el comportamiento.
4. Un commit coherente después de verificarlo.

Las pruebas automatizadas se centrarán en reglas y comportamientos.
La conectividad se comprobará también en un teléfono físico.

Los parámetros iniciales del piloto son:
- Hasta 254 direcciones por rango.
- Puertos disponibles: 22, 23, 80, 443, 445 y 3389.
- Hasta 20 conexiones simultáneas.
- Tiempo de espera de 1000 ms por conexión.
- Sin reintentos automáticos.

Estos parámetros podrán ajustarse con evidencia de las pruebas físicas.