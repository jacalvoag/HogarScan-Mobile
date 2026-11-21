# Política de seguridad

Esta política explica cómo comunicar posibles vulnerabilidades de
HogarScan Mobile sin exponer información que pueda afectar al proyecto
o a sus usuarios.

## Alcance de mantenimiento

El mantenimiento se concentra en la línea actual de desarrollo.
No se garantiza mantenimiento independiente de versiones anteriores.

HogarScan es un proyecto académico. El equipo revisa los reportes según
su disponibilidad, sin comprometer un plazo fijo de respuesta o resolución.

## Reportar una vulnerabilidad

Si el repositorio tiene habilitados los reportes privados de GitHub,
utiliza la opción **Report a vulnerability** de su apartado de seguridad.

No publiques detalles de una vulnerabilidad sin corregir en issues,
Pull Requests o comentarios públicos.

Si el reporte privado no está disponible, abre un issue solicitando
un canal privado de contacto, sin describir la vulnerabilidad ni
adjuntar pruebas sensibles.

Los errores de interfaz o funcionamiento sin impacto de seguridad
pueden reportarse mediante un issue normal.

## Información útil para el reporte

Incluye, cuando sea posible:

- Descripción del problema.
- Versión de la aplicación o commit afectado.
- Modelo del dispositivo y versión de Android.
- Condiciones necesarias para reproducirlo.
- Pasos mínimos utilizando cuentas y datos de prueba.
- Comportamiento observado y comportamiento esperado.
- Impacto que pudiste comprobar.

Distingue los hechos observados de las hipótesis.

No adjuntes contraseñas, tokens, bases de datos con información real,
archivos de firma ni datos de otras personas. Comparte únicamente la
evidencia necesaria y elimina los datos sensibles.

## Pruebas autorizadas

Utiliza dispositivos, cuentas, redes y servicios propios o expresamente
autorizados.

Esta política y el canal de reporte no constituyen una autorización
para probar servicios desplegados, cuentas ajenas o redes de terceros.

## Tratamiento del reporte

El equipo analizará el alcance, intentará reproducir el problema y
coordinará la corrección y su comunicación.

Los reportes pueden afectar, entre otras áreas, a:

- Almacenamiento o exposición de credenciales.
- Separación de datos entre cuentas.
- Restauración e invalidación de sesiones.
- Comunicaciones con la API y validación TLS.
- Privacidad del historial y de la información enviada.
- Dependencias y recursos incorporados a la aplicación.

Una alerta automática requiere revisar su contexto antes de determinar
su impacto.

## Hallazgos producidos por HogarScan

Una posible coincidencia CVE o una entrada del catálogo KEV mostrada
durante una evaluación no implica una vulnerabilidad de HogarScan.

La identificación anunciada o declarada de un servicio no se verifica
de forma independiente. Sus posibles coincidencias requieren contrastar
el producto, la versión y las condiciones aplicables.

Los problemas del propio proceso de identificación o evaluación,
la autenticación, el aislamiento de cuentas o la privacidad sí pueden
corresponder a esta política.

Los problemas específicos de la API se reportan mediante la
[política de seguridad del backend](https://github.com/jacalvoag/HogarScan-Backend/blob/develop/SECURITY.md).