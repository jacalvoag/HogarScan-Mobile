# Alcance original: evidencia, orientación y borrado

La propuesta U2, páginas 2–4, requiere reconocer protocolos mediante respuestas
compatibles, orientar la revisión con evidencia y consultar CVE solo con identidad
suficiente. Ningún puerto, banner ni pertenencia a KEV certifica explotación.

## Implementación

- El escaneo admite siete opciones (21, 22, 23, 80, 443, 445, 3389), con un máximo
  de seis seleccionadas y 254 direcciones. Mantiene autorización, cancelación,
  concurrencia y comprobación del Wi-Fi. No inicia sesión en equipos.
- Una conexión aceptada puede inspeccionarse durante un máximo adicional de
  2000 ms y hasta 8192 bytes. El fallo de identificación conserva ACCEPTED.
- SSH exige identificador compatible; FTP exige una bienvenida que indique FTP
  explícitamente (un 220 SMTP no basta). HTTP usa HEAD y valida la respuesta.
  HTTPS exige verificación TLS y una respuesta HTTP; no se desactiva la validación
  de certificados. Un certificado local no confiable puede impedir identificarlo.
- Solo se almacenan protocolo e identidad normalizada. Banners, cabeceras,
  certificados, nombres de equipos e IP privadas no se envían a la API.
- El catálogo inicial extrae identidades anunciadas de Apache HTTP Server,
  nginx y OpenSSH cuando incluyen una versión explícita. No completa versiones
  faltantes ni supone parches o configuraciones. Estos anuncios pueden mentir.
- Una identidad inequívoca se usa automáticamente para NVD. Con varios productos
  en un objetivo se solicita seleccionar uno; no se toma el primero ni se pretende
  evaluar todos los productos de un inventario. La selección queda ligada al puerto.
  La identificación manual sigue disponible como procedencia distinta y no se
  atribuye a un puerto observado.
- La API debe desplegar primero el contrato 0.4.0 (`SERVICE_RESPONSE` y
  `service_port`). Después puede distribuirse este móvil. Hasta entonces las
  evaluaciones que usan el nuevo contrato pueden ser rechazadas, sin perder TCP.
- Room 4 añade evidencia nullable y el puerto de identidad; migra desde 1, 2 y 3.
  Las revisiones antiguas no reciben evidencia inventada ni se reevalúan al abrirse.

## Interpretación

Las reglas 0.4.0 del backend están versionadas en JSON. Distinguen hechos de
orientaciones preventivas y coincidencias CVE. LOW/MEDIUM/HIGH son prioridades
de revisión, no porcentajes, CVSS ni probabilidad de explotación. HTTP/FTP merecen
revisión del cifrado; SSH exige revisar autorización; HTTPS no certifica la app.
Los puertos 23/445/3389 sin protocolo solo orientan una comprobación de su uso
habitual. NVD sustenta posibles coincidencias; KEV positivo eleva su prioridad.
KEV falso o desconocido no significa ausencia de riesgo. Los estados de fuentes,
referencias y fechas permanecen visibles.

## Borrado local

Desde el detalle se puede confirmar la eliminación de una revisión cerrada del
perfil actual. El DAO filtra propietario y rechaza revisiones RUNNING. Sesión y
transacción coordinan el borrado; invitado también queda protegido ante login.
Se eliminan en cascada puertos, observaciones, evaluaciones y pendientes locales.
Los resúmenes del servidor permanecen; un envío ya iniciado puede terminar.
El borrado remoto es una operación independiente con su propia confirmación.

## Comprobaciones reproducibles

1. Ejecutar unitarias, APK y APK de instrumentación; después instrumentadas en
   el Samsung: `./gradlew :app:connectedDebugAndroidTest`.
2. Comparar en la misma red y momento con Nmap, una sola IP privada autorizada:
   `powershell -File scripts/compare-authorized-scan.ps1 -TargetIPv4 192.168.101.32 -Ports 21,22,80,443 -Authorized`.
3. Usar casos HTTP con Server explícito, HTTP sin versión, SSH, FTP, TCP silencioso,
   puerto cerrado, respuesta ambigua y HTTPS con certificado no confiable.
4. Comprobar protocolo/identidad local, privacidad del JSON y orientación recibida.
   La comparación Nmap no demuestra vulnerabilidad ni sustituye la revisión CVE.
5. Verificar migración conservando invitados/cuentas, borrado de una cuenta sin
   afectar otra, cancelación de confirmación y bloqueo del borrado en curso.
6. Comprobar fuentes disponibles e indisponibles. El ensayo CVE con identificación
   simulada comprueba correlación, no que el software exista en el equipo.

Las comprobaciones automatizadas locales y las pruebas manuales en la red
doméstica se registran por separado. La validación manual se realizó con el
Samsung SM-A315G, Android 12, y servicios de prueba controlados por el equipo.
Los resultados acreditan únicamente los casos y las condiciones observadas.

## Evidencia local de referencia (19 de noviembre de 2026)

Se compiló Nmap 7.97 y se ejecutó contra fixtures propios en loopback Linux.
Se cargó y ejecutó el ServiceInspector compilado del móvil sobre las mismas
respuestas. Se usaron puertos altos locales por los permisos del entorno; el
inspector recibió el puerto lógico del piloto. No fue una revisión Android ni
una prueba de la red doméstica.

| Puerto de fixture / lógico | Inspector | Nmap |
|---|---|---|
| 2121 / 21 | ACCEPTED, FTP | open, ftp |
| 2222 / 22 | ACCEPTED, SSH, openssh 9.6p1 | open, ssh, OpenSSH 9.6p1 |
| 8080 / 80 | ACCEPTED, HTTP, http_server 2.4.49 | open, http, Apache httpd 2.4.49 |
| 2323 / 23 | REFUSED | closed |

Los nombres/versiones de estos servicios eran anuncios simulados en fixtures;
la concordancia no certifica software instalado ni vulnerabilidad. Las unitarias
además prueban receptor silencioso, respuestas truncadas/ambiguas y ausencia de
versión; en esos casos se conserva la incertidumbre.

## Validación manual en Android y red doméstica

Comprobaciones realizadas el 19 de noviembre de 2026 y confirmadas por el
responsable de las pruebas, utilizando un Samsung SM-A315G con Android 12.

| Caso | Resultado observado |
|---|---|
| HTTP sin producto o versión identificables | Conexión aceptada, protocolo HTTP identificado y orientación preventiva. NVD y KEV no consultadas. |
| HTTP con anuncio simulado Apache/2.4.49 | Identificación automática de apache/http_server/2.4.49 asociada al puerto 80. NVD y KEV disponibles. |
| Coincidencias del anuncio Apache | CVE-2021-41773 y CVE-2021-42013 con prioridad alta y presencia en KEV; CVE-2021-41524 con prioridad media y ausencia en el catálogo consultado. Se conservaron las advertencias de coincidencia posible. |
| FTP en 21 y SSH en 22 sin producto y versión | Por cada servicio se presentaron conexión aceptada, protocolo identificado y revisión preventiva. NVD y KEV no consultadas. |
| Puerto 443 con certificado autofirmado no confiable | Se conservó la conexión TCP aceptada, sin identificar HTTPS ni consultar NVD o KEV. |
| Servicio HTTP detenido | Nmap desde Windows informó closed; el teléfono registró tiempo agotado y un resultado inconcluso, sin inventar protocolo ni coincidencias CVE. |
| Historial y evaluaciones | Se conservaron al cerrar y reabrir la aplicación. |
| Estadísticas | Se actualizaron después de evaluar la revisión y sincronizar su resumen. Completar únicamente la revisión TCP no incrementó las estadísticas de la cuenta. |
| Separación de cuentas | Las estadísticas y operaciones de una cuenta no modificaron las de otra. |
| Borrado local | La revisión se eliminó del historial del propietario correspondiente y el borrado persistió al reabrir la aplicación. |
| Borrado remoto | Cancelar conservó los resúmenes. Confirmar eliminó los de la cuenta correspondiente y conservó el historial local. Abrir evaluaciones antiguas no los recreó. |
| Recuperación de conectividad | La revisión TCP permaneció guardada tras fallar la evaluación sin conexión. Al recuperar conectividad se evaluó y sincronizó la misma revisión sin duplicar su conteo. |
| Rotación y navegación | El hallazgo permaneció abierto al girar. Atrás regresó a la misma revisión y después al historial. Cambiar de pestaña permitió volver y abrir otra revisión. |
| Restauración de la aplicación | Se conservaron sesión, historial y evaluaciones al cerrar y reabrir la aplicación. |

La comparación física utilizó Nmap 7.991 en Windows. Con el servicio activo,
Nmap identificó el puerto 80 como open, http, Apache httpd 2.4.49. Se utilizó
una respuesta simulada: no se instaló una versión vulnerable de Apache.

Los resultados del PC y del teléfono corresponden a sus respectivos orígenes
de conexión. Un tiempo agotado observado desde el teléfono no se transforma
en puerto cerrado por el resultado obtenido desde el PC.

## Verificación de la corrección de navegación

La corrección conservó el hallazgo seleccionado en ScanHistoryViewModel.
La selección se descarta al abrir otra revisión, cerrar el detalle o cambiar
de sesión. Volver desde el hallazgo mantiene abierta la revisión correspondiente.

- 414 pruebas unitarias aprobadas, sin errores ni omisiones.
- Compilación correcta de la aplicación y de la APK de instrumentación.
- Rotación, navegación y persistencia confirmadas manualmente en el teléfono.
- La nueva prueba ScanFindingRotationTest quedó compilada; su ejecución
  instrumentada no forma parte de esta confirmación manual.
