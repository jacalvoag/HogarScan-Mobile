# Integración propuesta de estadísticas

El bloque `statistics` está aislado y todavía no está conectado a la navegación
ni a `AppContainer`. Consume `GET /me/stats` a través de `SessionApiClient` y
no recibe ni conserva tokens. Sus totales cuentan resúmenes sincronizados,
no el historial local ni dispositivos únicos.

Para la integración coordinada por José:

1. Construir `HttpStatisticsGateway(sessionApiClient)` y proporcionar el
   gateway y `sessionReader` a `StatisticsViewModel` desde la composición.
2. Mostrar `StatisticsRoute` en la navegación acordada. La pantalla ofrece una
   acción para llamar a `refresh`; la integración también puede solicitar una
   actualización al entrar, tras sincronizar o después de borrar resúmenes.
3. El ViewModel limpia los datos al cambiar cuenta, contexto o generación.
   Descarta respuestas tardías y no muestra una respuesta cuya generación no
   coincida con la sesión. La actualización de generación y el borrado remoto
   siguen a cargo de la infraestructura compartida; no se intentan aquí.
4. Ante una generación distinta devuelta por la API, coordinar la actualización
   de la sesión antes de volver a consultar. No se modifican generaciones ni se
   reenvían resúmenes antiguos desde este bloque.

Los tests unitarios usan el cliente simulado. Quedan pendientes las pruebas
contra la API real, la navegación y las pruebas con dos cuentas en teléfonos.
