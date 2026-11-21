# HogarScan Mobile

Aplicación Android para realizar revisiones TCP acotadas en una red doméstica,
consultar los resultados y obtener orientación sobre los servicios observados.

HogarScan distingue entre una conexión aceptada, un protocolo identificado,
una recomendación preventiva y una posible coincidencia con una CVE.
Estos resultados no confirman por sí solos que un dispositivo sea vulnerable.

Proyecto académico desarrollado por José Antonio Calvo Aguilar y
Uzzi Yazziel Gonzaga Gómez.

## Funcionalidades

### Revisión de la red

- Consulta de la IPv4 y subred de la conexión Wi-Fi.
- Configuración de un rango de hasta 254 direcciones.
- Selección de hasta seis puertos entre 21, 22, 23, 80, 443, 445 y 3389.
- Autorización del usuario antes de iniciar la revisión.
- Registro de conexiones aceptadas, rechazadas, tiempos agotados y errores.
- Cancelación de la revisión conservando los resultados parciales.
- Comprobación de cambios o pérdida de la conexión autorizada.

### Identificación de servicios

La aplicación puede identificar HTTP, HTTPS, FTP y SSH cuando recibe
evidencia compatible con el protocolo.

También reconoce determinados anuncios de Apache HTTP Server, nginx y
OpenSSH cuando incluyen una versión explícita. La identificación anunciada
por un servicio no se verifica de forma independiente.

La conexión al puerto 443 no basta para identificar HTTPS: se requiere
validar TLS y recibir una respuesta HTTP compatible.

### Evaluaciones y hallazgos

Las observaciones normalizadas se envían a la API para obtener:

- Interpretaciones de los resultados TCP.
- Identificación del protocolo observado.
- Orientaciones preventivas.
- Posibles coincidencias CVE cuando existe identificación suficiente.
- Estado de las consultas a NVD y al catálogo KEV de CISA.

Cada hallazgo presenta su evidencia, categoría, prioridad de revisión,
nivel de certeza y recomendaciones.

La identificación manual de un producto se distingue de la identificación
anunciada por un servicio.

### Cuentas y persistencia

- Registro, inicio y cierre de sesión.
- Restauración de la sesión al reabrir la aplicación.
- Credenciales locales protegidas con AES-GCM y Android Keystore.
- Historial de revisiones y evaluaciones almacenado con Room.
- Separación del historial por propietario.
- Revisiones locales en modo invitado.
- Eliminación de revisiones locales mediante confirmación.

El historial del invitado no se asigna automáticamente a una cuenta al
iniciar sesión.

### Sincronización y estadísticas

La aplicación sincroniza resúmenes de revisiones completas y evaluadas.
Las estadísticas de la cuenta incluyen únicamente los resúmenes recibidos
por la API.

Por lo tanto, completar una revisión TCP no incrementa las estadísticas
hasta que se evalúa y se sincroniza su resumen.

Las estadísticas muestran conteos de revisiones, direcciones revisadas,
servicios con conexión aceptada y hallazgos por categoría. Una dirección
puede aparecer en varias revisiones; estos conteos no representan
dispositivos únicos.

El borrado del historial local y el borrado de los resúmenes del servidor
son operaciones independientes.

## Tecnologías

| Área | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose y Material 3 |
| Presentación | ViewModels y flujos de estado |
| Concurrencia | Kotlin Coroutines |
| Persistencia local | Room |
| Comunicación HTTP | OkHttp y Moshi |
| Protección de credenciales | Android Keystore |
| Recursos SVG | AndroidSVG |
| Compilación | Gradle Wrapper |

Las versiones de las dependencias están definidas en
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Arquitectura

El proyecto utiliza un módulo Android y organiza el código por
funcionalidad, con separación entre presentación, dominio y datos.

- **Presentación:** pantallas Compose, ViewModels y estados de interfaz.
- **Dominio:** modelos, reglas, casos de uso y contratos.
- **Datos:** conexiones TCP, adaptadores Android, Room y comunicación HTTP.
- **Composición:** construcción y conexión de dependencias mediante
  `AppContainer`.

Las pantallas no acceden directamente a sockets, Room ni clientes HTTP.

La aplicación conserva el detalle de las revisiones en el teléfono.
La API interpreta las observaciones normalizadas y almacena los resúmenes
estadísticos en PostgreSQL. El móvil no se conecta directamente a esa
base de datos.

Backend:
[HogarScan Backend](https://github.com/jacalvoag/HogarScan-Backend).

## Requisitos

- Android Studio compatible con Android Gradle Plugin 9.0.1.
- JDK 21, utilizado para las comprobaciones de compilación del proyecto.
- Android SDK 36.
- Dispositivo o emulador con Android 7.0 —API 24— o superior.
- Acceso a una API HogarScan compatible para autenticación, evaluaciones,
  sincronización y estadísticas.
- Una red Wi-Fi autorizada para realizar las revisiones.

El repositorio incluye Gradle Wrapper 9.1.0. No es necesario instalar
Gradle por separado.

Las comprobaciones de conectividad real deben realizarse en un teléfono
con acceso a la red de prueba. El comportamiento de un emulador puede
diferir del de un dispositivo físico.

## Configuración

### 1. Obtener el proyecto

```bash
git clone https://github.com/jacalvoag/HogarScan-Mobile.git
cd HogarScan-Mobile
git switch develop
```

### 2. Configurar Android Studio

Abre la carpeta del proyecto, instala los componentes del SDK solicitados
y selecciona JDK 21 como Gradle JDK.

Android Studio puede generar `local.properties` para indicar la ubicación
local del SDK. Este archivo depende de cada equipo y no debe subirse al
repositorio.

### 3. Configurar la API

La URL se recibe mediante la propiedad de Gradle
`HOGARSCAN_API_BASE_URL`.

Puedes agregarla al archivo personal `~/.gradle/gradle.properties`:

```properties
HOGARSCAN_API_BASE_URL=https://api.example.com/
```

Sustituye el dominio de ejemplo por el servidor real de HogarScan.

La URL debe:

- Utilizar HTTPS.
- Apuntar a la raíz del servidor.
- No incluir credenciales, parámetros ni fragmentos.
- Utilizar un certificado confiable para el dispositivo Android.

La API debe estar accesible desde el teléfono. `localhost` en un teléfono
se refiere al propio teléfono.

No agregues credenciales de PostgreSQL, tokens ni contraseñas a los
archivos del proyecto. La URL de la API se incorpora al APK y no debe
considerarse un secreto.

También puedes proporcionar la propiedad al compilar:

```bash
./gradlew :app:assembleDebug -PHOGARSCAN_API_BASE_URL=https://api.example.com/
```

Cambiar esta propiedad requiere volver a compilar e instalar la aplicación.

## Compilación y ejecución

Los siguientes comandos se ejecutan desde la raíz del repositorio en
Bash o Git Bash. En PowerShell, sustituye `./gradlew` por
`.\gradlew.bat`.

### Compilar la aplicación

```bash
./gradlew :app:assembleDebug
```

APK generado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Instalar en un dispositivo conectado

```bash
./gradlew :app:installDebug
```

También puedes ejecutar la aplicación desde Android Studio.

## Verificación

### Integración continua

El workflow `Android CI` se ejecuta en Pull Requests hacia `develop`
y `main`, y en los cambios integrados en esas ramas.

Comprueba:

- Pruebas unitarias.
- Android Lint.
- Compilación de la aplicación.
- Compilación de la APK de pruebas instrumentadas.

Los informes generados se conservan como artefactos durante siete días.

La CI utiliza una URL de ejemplo y no necesita credenciales de la API.
No ejecuta las pruebas instrumentadas ni sustituye las comprobaciones
manuales en una red autorizada.

### Pruebas unitarias

```bash
./gradlew :app:testDebugUnitTest
```

### Análisis estático de Android

```bash
./gradlew :app:lintDebug
```

### Compilar las pruebas instrumentadas

```bash
./gradlew :app:assembleDebugAndroidTest
```

Este comando compila la APK de pruebas; no ejecuta las pruebas.

### Ejecutar las pruebas instrumentadas

Con un dispositivo o emulador conectado:

```bash
./gradlew :app:connectedDebugAndroidTest
```

### Comprobar el formato del cambio

```bash
git diff --check
```

Las pruebas automatizadas se complementan con comprobaciones manuales
de conectividad, navegación, rotación, persistencia y separación de cuentas.
El registro de los casos comprobados se encuentra en
[`docs/alcance-original-y-validacion.md`](docs/alcance-original-y-validacion.md).

## Uso básico

1. Conecta el teléfono a una red Wi-Fi que tengas autorización para revisar.
2. Configura el rango de direcciones y los puertos.
3. Confirma el alcance e inicia la revisión.
4. Consulta los resultados TCP y los protocolos identificados.
5. Inicia sesión para evaluar las observaciones mediante la API.
6. Abre los hallazgos y consulta su evidencia y recomendaciones.
7. Comprueba la sincronización del resumen para verlo en las estadísticas.

Las revisiones y evaluaciones guardadas pueden consultarse desde el historial.

## Alcance y limitaciones

- La revisión utiliza conexiones TCP acotadas; no realiza explotación,
  intentos de autenticación ni captura de tráfico.
- Una conexión aceptada demuestra únicamente que se aceptó ese intento TCP.
- Un tiempo agotado no confirma que el equipo esté ausente ni que el puerto
  esté cerrado.
- Un puerto conocido no basta para identificar el protocolo.
- La identificación de protocolos y productos se limita a las respuestas
  que la aplicación puede interpretar.
- Un certificado no confiable puede impedir identificar HTTPS aunque la
  conexión TCP haya sido aceptada.
- Una versión anunciada o declarada puede ser incompleta o incorrecta.
- Una posible coincidencia CVE no confirma una vulnerabilidad.
- La prioridad de un hallazgo orienta su revisión; no es un índice global
  de seguridad ni una probabilidad de explotación.
- La ausencia de una coincidencia en KEV no demuestra ausencia de riesgo.
- La revisión no garantiza descubrir todos los dispositivos de la red.
- La sincronización de resúmenes no constituye un respaldo completo del
  historial local.

Utiliza la aplicación únicamente en redes y equipos propios o con
autorización de su responsable.

## Documentación

- [Alcance, interpretación y validación](docs/alcance-original-y-validacion.md).
- [Base visual y navegación](docs/base-visual-y-navegacion.md).
- [Arquitectura inicial](docs/arquitectura.md).
- [Notas de desarrollo e integración](docs/desarrollo-movil.md).

Los documentos de arquitectura inicial y desarrollo conservan decisiones
y etapas del proceso. Sus apartados históricos de trabajo pendiente deben
interpretarse considerando la implementación actual.

## Contribución y seguridad

Consulta la [guía de contribución](CONTRIBUTING.md) para conocer el flujo
de trabajo y las verificaciones del proyecto.

Para comunicar posibles vulnerabilidades, sigue la
[política de seguridad](SECURITY.md).

## Autores

- José Antonio Calvo Aguilar — [jacalvoag](https://github.com/jacalvoag).
- Uzzi Yazziel Gonzaga Gómez — [uzzigg](https://github.com/uzzigg).

## Licencia

El código propio del proyecto se distribuye bajo la
[licencia MIT](LICENSE).

Los componentes y recursos de terceros conservan sus respectivas
licencias. Los avisos de las tipografías incluidas se encuentran en
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).