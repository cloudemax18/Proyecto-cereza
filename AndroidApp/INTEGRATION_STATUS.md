# Estado de integración

- [x] Confirmar los DBF reales: `S12-Sep08/DData/chery.dbf`, `M11/DData/chery.dbf` y `S21-Sep08/DData/chery.dbf`.
- [x] Confirmar las carpetas de ilustraciones: `Pictures/` (con mayúscula).
- [x] Añadir un lector Kotlin que descarga el DBF real por HTTPS y conserva sus columnas.
- [x] Etiquetar los registros con el vehículo de su carpeta y filtrar el catálogo por selección.
- [x] Añadir textos en español, inglés y ruso.
- [ ] Confirmar nombres de columnas y ajustar campos visibles de código, descripción, cantidad y ubicación.
- [ ] Conectar `RemoteDbfRepository.loadParts()` a la pantalla principal y gestionar carga, errores y falta de Internet.
- [ ] Validar las ilustraciones; algunos archivos `.mjs` pueden ser SWF/Flash.
- [ ] Compilar y probar en Android Studio.

Solo se leen los archivos `chery.dbf`, no tablas de cuentas/usuarios. La descarga requiere Internet; para modo sin conexión se necesita caché local.
