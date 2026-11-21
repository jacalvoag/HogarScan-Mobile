# Guía de contribución

HogarScan Mobile es un proyecto académico desarrollado en equipo.
Esta guía explica cómo proponer, implementar y verificar cambios.

Antes de comenzar, consulta el [README](README.md) y el
[alcance de la aplicación](docs/alcance-original-y-validacion.md).

## Coordinación

Coordina previamente los cambios que afecten:

- Navegación y componentes visuales compartidos.
- Autenticación, sesión y separación de cuentas.
- Esquemas de Room y migraciones.
- Contratos HTTP y compatibilidad con el backend.
- Evaluaciones, sincronización y estadísticas.
- Dependencias y configuración de compilación.
- Límites de revisión y tratamiento de la evidencia.

Los cambios internos de una funcionalidad pueden avanzar de forma
independiente mientras respeten los contratos acordados.

No es obligatorio crear un issue para cada cambio interno del equipo.
Para propuestas externas, conviene describir primero el problema y
el alcance esperado.

## Ramas

- `develop` integra el trabajo de desarrollo.
- `main` se reserva para la entrega estable.
- Los cambios se realizan en ramas de trabajo.
- Los Pull Requests se dirigen a `develop`.
- Cada rama agrupa un bloque de trabajo y puede contener varios commits.

Utiliza nombres descriptivos:

```text
feat/nombre-del-bloque
fix/descripcion-del-problema
docs/tema
chore/tarea
```

Actualiza la base antes de comenzar y revisa los cambios recientes antes
de integrar. No reescribas ramas compartidas ni descartes cambios ajenos
al resolver conflictos.

La promoción de `develop` a `main` se coordina como parte de la entrega.

## Commits

- Mantén commits pequeños, coherentes y fáciles de revisar.
- Describe el comportamiento o resultado del cambio.
- Separa los reformateos generales de los cambios funcionales.
- Evita incluir archivos ajenos al objetivo del commit.
- Incluye las pruebas relevantes cuando cambia un comportamiento.

Ejemplos:

```text
feat: mostrar el estado de sincronización
fix: conservar el hallazgo al girar la pantalla
docs: explicar la configuración de la API
```

## Convenciones técnicas

- Conserva la separación entre presentación, dominio y datos.
- Las pantallas no deben acceder directamente a sockets, Room ni HTTP.
- Recibe las dependencias mediante los mecanismos existentes del proyecto.
- Mantén las credenciales fuera de las pantallas y los ViewModels.
- Comprueba el propietario y la sesión al trabajar con datos de una cuenta.
- Conserva la propagación de cancelación en las operaciones asíncronas.
- No sustituyas migraciones de Room por borrados destructivos del historial.
- Actualiza las migraciones y los esquemas exportados cuando cambie Room.
- Declara las versiones de dependencias en el catálogo de Gradle.
- No desactives la validación TLS para resolver problemas de conexión.
- Conserva las diferencias entre observación, interpretación y posible CVE.

Los cambios de contrato deben coordinarse con
[HogarScan Backend](https://github.com/jacalvoag/HogarScan-Backend).

## Interfaz y navegación

Utiliza los componentes, colores, tipografías e iconos compartidos.

Cuando el cambio afecte a la interfaz, comprueba:

- Modo claro y oscuro.
- Distinción entre elementos seleccionados y no seleccionados.
- Legibilidad con tamaños de texto mayores.
- Rotación y conservación del estado.
- Navegación mediante Atrás.
- Contenido desplazable y ausencia de elementos recortados.
- Estados de carga, vacío, error y recuperación.

Añade descripciones accesibles a los controles que las necesiten.
Los elementos decorativos no deben anunciar información redundante.

## Verificación

Ejecuta los comandos desde la raíz del repositorio.
En PowerShell, sustituye `./gradlew` por `.\gradlew.bat`.

### Cambios funcionales

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
git diff --check
```

### Cambios que afecten a pruebas instrumentadas

Compila la APK de pruebas:

```bash
./gradlew :app:assembleDebugAndroidTest
```

Ejecuta las pruebas con un dispositivo o emulador conectado:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Compilar las pruebas no equivale a ejecutarlas. Indica por separado
qué verificaciones se realizaron.

Selecciona pruebas que comprueben el comportamiento modificado.
Los cambios de persistencia deben comprobar la conservación de datos;
los de sesión, la separación de cuentas; y los de navegación,
la conservación del estado correspondiente.

Las pruebas automatizadas deben utilizar datos controlados y respuestas
simuladas cuando corresponda. No deben depender de credenciales personales
ni de servicios externos reales para comprobar reglas internas.

Las pruebas manuales de red se realizan por separado, únicamente sobre
equipos propios o expresamente autorizados.

### Cambios exclusivamente documentales

Revisa el contenido, los enlaces, los comandos y el diff:

```bash
git diff --check
```

No es necesario repetir las pruebas de la aplicación por cambios que
únicamente modifican documentación.

## Pull Requests

Describe:

- El problema y el comportamiento resultante.
- Los cambios relevantes para revisar la solución.
- Las verificaciones realizadas y sus resultados.
- El impacto en datos, contratos, configuración o recursos compartidos.
- Las limitaciones que permanezcan.

Para cambios visuales, incluye capturas útiles de los estados afectados.
Si el cambio modifica colores o navegación, muestra los modos claro
y oscuro cuando corresponda.

No presentes una prueba compilada como ejecutada ni una revisión visual
como una comprobación completa del funcionamiento.

Espera los resultados de las comprobaciones automáticas configuradas
antes de integrar.

## Información privada

No incluyas contraseñas, tokens, credenciales de servicios, bases de datos
locales ni datos personales en commits, issues, capturas o Pull Requests.

Oculta los datos privados de la red cuando compartas evidencias.

No subas `local.properties`, rutas personales del SDK, archivos de firma
ni contraseñas de keystores.

Para posibles vulnerabilidades del proyecto, sigue la
[política de seguridad](SECURITY.md).