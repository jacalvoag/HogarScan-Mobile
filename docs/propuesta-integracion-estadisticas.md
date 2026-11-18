# Integración de estadísticas

La rama de integración conecta `statistics` a `AppContainer` y a la pestaña
de estadísticas. Consume `GET /me/stats` a través de `SessionApiClient` y
no recibe ni conserva tokens. Sus totales cuentan resúmenes sincronizados,
no el historial local ni dispositivos únicos.

Comportamiento para revisar con José:

1. `AppContainer` construye `HttpStatisticsGateway(sessionApiClient)` y entrega
   el gateway y `sessionReader` a `StatisticsViewModel`.
2. `StatisticsRoute` consulta al entrar o volver a la pestaña, y ofrece una
   acción de actualización manual después de sincronizar o borrar resúmenes.
3. El ViewModel limpia los datos al cambiar cuenta, contexto o generación, y
   también cuando vence la sesión mientras la pantalla permanece abierta.
   Descarta respuestas tardías y no muestra una respuesta cuya generación no
   coincida con la sesión. La actualización de generación y el borrado remoto
   se realizan mediante la infraestructura compartida.
4. Ante una generación mayor devuelta por la API se actualiza la sesión y se
   invalidan pendientes anteriores antes de volver a consultar. Una generación
   inferior se presenta como error y nunca se adopta automáticamente.

Las pruebas unitarias e instrumentadas usan datos y transporte simulados; la
navegación a la pestaña está integrada. Quedan pendientes las pruebas contra la
API real con sesión y las pruebas con dos cuentas en teléfonos.
