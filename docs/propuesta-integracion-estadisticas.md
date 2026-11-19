# Integración de estadísticas

La funcionalidad statistics está conectada a AppContainer y a la pestaña
Estadísticas. Consume GET /me/stats mediante SessionApiClient y no recibe
ni conserva tokens.

## Datos contabilizados

Los totales incluyen revisiones completas y evaluadas cuyos resúmenes
se sincronizaron con la API.

Guardar una revisión TCP en el historial local no incrementa por sí solo
las estadísticas. Primero deben completarse la evaluación y la sincronización
del resumen.

Los conteos no representan dispositivos únicos. Una dirección puede
contabilizarse en varias revisiones.

## Comportamiento

1. AppContainer construye HttpStatisticsGateway y proporciona el gateway
   y SessionReader a StatisticsViewModel.
2. StatisticsRoute consulta al entrar o volver a la pestaña y ofrece
   actualización manual.
3. El ViewModel limpia los datos al cambiar cuenta, contexto o generación,
   y cuando vence la sesión. Descarta respuestas tardías de otros contextos.
4. Una generación mayor devuelta por la API actualiza la sesión e invalida
   pendientes anteriores antes de volver a consultar. Una generación inferior
   se presenta como error.
5. El borrado remoto de resúmenes conserva el historial local y modifica
   únicamente las estadísticas de la cuenta correspondiente.
6. Abrir una evaluación antigua no vuelve a crear un resumen eliminado.

## Validación manual

El 19 de noviembre de 2026 se confirmó en el teléfono:

- Actualización de estadísticas después de evaluar y sincronizar.
- Separación de los resultados de las cuentas A y B.
- Cancelación y confirmación del borrado remoto.
- Conservación del historial local después del borrado remoto.
- Ausencia de recreación de resúmenes al abrir evaluaciones antiguas.
- Conteo de una nueva revisión evaluada después del borrado.
- Recuperación de conectividad y sincronización sin duplicar el conteo.
- Navegación entre Estadísticas e Historial sin perder acceso a las revisiones.

Las pruebas automatizadas con transporte simulado y estas comprobaciones
manuales constituyen evidencias distintas.