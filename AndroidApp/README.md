# Chery SPMS Android — catálogo multimodelo

Aplicación Android con Jetpack Compose para consultar el catálogo DBF y visualizar las ilustraciones de repuestos de los modelos S12, M11 y S21.

## Compilar
Abre `AndroidApp/` en Android Studio reciente, con JDK 17 y Android SDK Platform 35. Sincroniza Gradle y ejecuta el módulo `app` en un dispositivo Android 8.0 (API 26) o superior.

## Datos por modelo
- S12-Sep08: `S12-Sep08/DData/chery.dbf`; dibujos en `S12-Sep08/pictures/`.
- M11: `M11/DData/chery.dbf`; dibujos en `M11/Pictures/`.
- S21-Sep08: `S21-Sep08/DData/chery.dbf`; dibujos en `S21-Sep08/Pictures/`.

La app descarga el DBF y la ilustración seleccionada desde la rama `main` por HTTPS. Se requiere Internet la primera vez que se abre cada ilustración. Una vez convertida y guardada, esa imagen se puede mostrar sin conexión.

## Ilustraciones y caché local
Los 5.004 archivos `.mjs` del repositorio son películas SWF/Flash (no imágenes PNG/JPEG). Para evitar que el APK crezca cientos de megabytes, la app convierte cada diagrama **bajo demanda**: lo renderiza con Ruffle en WebView, captura el resultado como WebP y lo guarda en la base de datos SQLite local `chery_diagrams.db`. Las visitas posteriores usan la imagen WebP almacenada, incluso sin conexión. La caché tiene un límite de 100 MB y elimina las ilustraciones más antiguas cuando lo supera.

El índice `app/src/main/assets/diagram-index.json` relaciona los códigos normalizados de las piezas con los nombres reales de los dibujos. La búsqueda considera el código de pieza, la referencia de diagrama, la categoría y los campos jerárquicos del DBF.

## Funciones
- Catálogo para S12, M11 y S21.
- Búsqueda por código, nombre, categoría y campos DBF.
- Selector de vehículo e idioma (Español, English, Русский), con preferencias guardadas.
- Transiciones suaves al seleccionar piezas y expandir campos técnicos.
- Icono de aplicación adaptativo personalizado.
- Datos del catálogo consultados desde `chery.dbf`; no se importan `account.dbf` ni `FOXUSER.DBF`.

## Notas
- La cantidad mostrada es el valor del DBF y no garantiza existencias físicas.
- Si un dibujo no se ha almacenado todavía, se necesita Internet para cargar Ruffle y el SWF original.
