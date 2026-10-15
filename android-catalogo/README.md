# Catálogo Cereza para Android

Aplicación nativa Android con Kotlin, Jetpack Compose y SQLite.

## Abrir y compilar
1. Abre la carpeta `android-catalogo` con Android Studio (Hedgehog o posterior).
2. Usa JDK 17 y permite que Gradle sincronice las dependencias.
3. Ejecuta la configuración `app` en un emulador o teléfono Android (Android 8.0 o posterior).
4. Para generar APK: **Build > Build APK(s)**.

## Datos
La primera ejecución descarga `piezas.csv` desde `v1 web/importar/piezas.csv` y lo guarda en la base de datos SQLite del dispositivo. La búsqueda y los favoritos se administran localmente.

## Imágenes
La app construye la URL de la fotografía usando el código de pieza: `v1 web/importar/fotos/<codigo>.jpg`. En el conjunto original hay archivos con sufijos como `_1` y `_2`; para asociar todas las fotografías hay que aplicar un índice de nombres de archivo y usarlo como relación código-imágenes. Si una pieza no tiene una imagen con el nombre exacto del código, la tarjeta mostrará el marcador vacío hasta completar esa asociación.

## Notas
- Requiere conexión a internet para importar por primera vez los datos y cargar las fotografías remotas.
- La base de datos local permite consultar los repuestos importados sin volver a descargar el CSV.
- El proyecto aún debe compilarse y probarse en Android Studio antes de distribuir un APK.
