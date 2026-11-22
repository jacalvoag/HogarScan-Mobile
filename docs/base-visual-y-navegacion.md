# Base visual y navegación

La base parte de `develop` en el commit `4ddede1` y adapta la página
«HogarScan · Flujo revisado». Los ejemplos de Figma no se usan como datos.
Las fuentes DM Sans, Outfit y Space Mono se incluyen localmente; sus licencias
están en `docs/licencias/fuentes`.

## Recursos compartidos

- `ui/theme`: `HogarScanTheme`, `HogarColors`, `Typography`, `DmSans`,
  `Outfit` y `SpaceMono`. El tema sigue la preferencia clara/oscura del
  sistema; el color dinámico se puede habilitar expresamente.
- `ui/components/HogarComponents.kt`: `HogarButton`, `HogarField`,
  `HogarNotice`, `HogarBackHeader`, `HogarStatusTag`, `HogarMetric`,
  `HogarEmptyState`, `HogarIcon` y `HogarIconImage`. El botón admite
  variantes secundaria y destructiva.
  Incluye una vista previa de los controles.
- `ui/components/HogarFeedback.kt`: `HogarConfirmation`,
  `HogarPasswordField`, `HogarValueRow` y `HogarLoading`. Los componentes
  reciben valores y acciones; no acceden a la sesión ni a repositorios.
- `ui/navigation`: `RootDestination` y `HogarBottomNavigation` exponen
  los destinos Inicio, Historial, Estadísticas y Cuenta.
- `assets/figma/revised`: iconos originales de los destinos, logo y Atrás.
  Se renderizan con AndroidSVG, dependencia ya presente en el proyecto.

Los tamaños, colores y controles pueden ampliarse en la base cuando un flujo
lo necesite. Los módulos mantienen sus propias tarjetas y decisiones de negocio.

## Puntos de integración

`MainActivity` es responsable del destino raíz y del nivel de Inicio
(`WifiPage`). Se conserva con `rememberSaveable`. La barra inferior se
presenta en las raíces; Configurar revisión, progreso y ayuda utilizan Atrás.
El ViewModel de historial mantiene la revisión y el hallazgo seleccionados.

`WifiRoute` recibe el estado de historial del propietario vigente y
`onOpenReview(id)`; abrir la última revisión utiliza el mismo ViewModel y la
misma ruta que el historial. No se duplican ni se reasignan revisiones.
`ScanSetupForm` mantiene callbacks para editar rango y puertos, revisar,
confirmar, revocar y comenzar. El controlador conserva la validación y la
autorización, incluida la nueva comprobación de red antes de confirmar.

La entrega reúne las catorce vistas del flujo revisado. Cuenta, ingreso y
registro se abren desde la navegación de raíz y los accesos del modo invitado.
Los resultados de una revisión guardada abren el detalle de una dirección
o el flujo de evaluación. La evaluación conserva su ViewModel al navegar entre
preparación, identificación, progreso y hallazgo.

Los resúmenes de Estadísticas muestran los conteos disponibles en el servidor.
En Inicio e Historial se muestran la fecha y el estado de cada revisión
desde la lista ligera local. El alcance y los conteos por dirección se
consultan al abrir los resultados.
