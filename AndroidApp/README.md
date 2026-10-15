# Chery SPMS Android — catálogo multimodelo

Aplicación Android con Jetpack Compose para consultar el catálogo DBF y abrir los diagramas de repuestos de los modelos S12, M11 y S21.

## Abrir y compilar
Abre la carpeta `AndroidApp/` en Android Studio reciente, con JDK 17 y Android SDK Platform 35. Sincroniza Gradle y ejecuta el módulo `app` en un dispositivo o emulador Android 8.0 (API 26) o superior.

## Fuentes por modelo
- S12-Sep08: `S12-Sep08/DData/chery.dbf`; dibujos en `S12-Sep08/pictures/`.
- M11: `M11/DData/chery.dbf`; dibujos en `M11/Pictures/`.
- S21-Sep08: `S21-Sep08/DData/chery.dbf`; dibujos en `S21-Sep08/Pictures/`.

La aplicación descarga el DBF y el dibujo seleccionado desde la rama `main` por HTTPS. Se necesita conexión a Internet. No se importan `account.dbf` ni `FOXUSER.DBF`.

## Funciones
1. Selecciona un vehículo y busca por código, nombre, referencia o campos DBF.
2. Toca una pieza para abrir su dibujo asociado.
3. Los dibujos originales son archivos SWF/Flash guardados con extensión `.mjs`. Se muestran mediante Ruffle en un WebView; Ruffle se carga desde un CDN, por lo que el dispositivo necesita Internet para mostrar los diagramas.
4. Cambia entre Español, English y Русский. La preferencia de idioma y vehículo se guarda localmente.
5. Los campos originales del DBF se pueden expandir en el detalle de la pieza.

## Notas sobre los datos
- El índice `app/src/main/assets/diagram-index.json` relaciona los códigos normalizados de las piezas con los nombres reales de los dibujos por modelo.
- Los campos DBF identificados incluyen `PARTNUMBER` (código), `NAME` (descripción), `DIRECTORY` (ruta/categoría), `QUANTITY` (cantidad del catálogo) y `NO`/`N1` (referencia de la ilustración, según el modelo).
- `QUANTITY` refleja el valor almacenado en el DBF; no garantiza inventario físico disponible.
- Los archivos de datos originales no se modifican. Si una pieza no tiene un archivo asociado, la app muestra un aviso en lugar de un recuadro de imagen vacío.
