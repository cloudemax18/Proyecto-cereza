# Chery SPMS Android — catálogo multimodelo

Proyecto Android Studio con Jetpack Compose para consultar el catálogo de repuestos de tres vehículos desde los DBF publicados en este repositorio.

## Abrir
Abre la carpeta `AndroidApp/` con Android Studio reciente, con JDK 17 y Android SDK Platform 35. Sincroniza Gradle y ejecuta el módulo `app` en un dispositivo o emulador con Android 8.0 (API 26) o superior.

## Modelos y archivos fuente
- S12-Sep08: `S12-Sep08/DData/chery.dbf`; ilustraciones en `S12-Sep08/pictures/`.
- M11: `M11/DData/chery.dbf`; ilustraciones en `M11/Pictures/`.
- S21-Sep08: `S21-Sep08/DData/chery.dbf`; ilustraciones en `S21-Sep08/Pictures/`.

La aplicación descarga el DBF del modelo seleccionado desde la rama `main` usando HTTPS. Se necesita conexión a Internet. No se importan `account.dbf` ni `FOXUSER.DBF`.

## Uso
1. Elige el vehículo en la lista superior.
2. La app descarga el DBF de ese vehículo y etiqueta cada registro con el ID de su carpeta.
3. Busca por código, nombre o cualquiera de los campos originales.
4. Cambia la interfaz entre Español, English y Русский; la preferencia de idioma y vehículo se conserva localmente.

## Limitaciones conocidas
- La lectura DBF conserva todos los campos, pero los alias de código, descripción, cantidad y ubicación son heurísticos. Hasta validar los nombres de columnas contra los tres DBF, algunos datos podrían mostrarse como campos genéricos. El valor llamado cantidad es el valor del campo DBF detectado, no una garantía de inventario físico.
- Esta pantalla conecta los catálogos DBF. El visor de diagramas aún requiere integración y prueba por modelo. Parte de las ilustraciones puede estar almacenada como SWF/Flash con extensión `.mjs`, formato que Android WebView no muestra de forma nativa.
- No se ha generado ni validado un APK en este entorno. Compila y prueba en Android Studio antes de distribuir.

Los archivos de datos originales del repositorio no se modifican.
