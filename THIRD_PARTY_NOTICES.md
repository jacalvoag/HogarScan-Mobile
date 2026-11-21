# Avisos de terceros

La licencia MIT de HogarScan se aplica al código propio del proyecto.
Las dependencias y los recursos de terceros conservan sus respectivas
licencias y avisos de autoría.

## Tipografías incluidas

La aplicación distribuye las siguientes tipografías en sus recursos:

| Tipografía | Aviso de autoría | Licencia |
|---|---|---|
| DM Sans | Copyright 2014 The DM Sans Project Authors | SIL Open Font License 1.1 |
| Outfit | Copyright 2021 The Outfit Project Authors | SIL Open Font License 1.1 |
| Space Mono | Copyright 2016 The Space Mono Project Authors | SIL Open Font License 1.1 |

Los textos completos de las licencias se conservan en:

- [DM Sans](docs/licencias/fuentes/dmsans-OFL.txt).
- [Outfit](docs/licencias/fuentes/outfit-OFL.txt).
- [Space Mono](docs/licencias/fuentes/spacemono-OFL.txt).

Estos avisos deben acompañar la redistribución de las tipografías conforme
a sus respectivas licencias.

## Dependencias de software

Las dependencias directas y sus versiones se declaran en
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) y se utilizan desde
[`app/build.gradle.kts`](app/build.gradle.kts).

Este archivo documenta los avisos de las tipografías incluidas; no sustituye
las licencias ni los avisos de las bibliotecas obtenidas mediante Gradle.

Para consultar las dependencias de ejecución de una compilación:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath