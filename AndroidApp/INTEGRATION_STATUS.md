# Estado de integración

- [x] Confirmar los DBF reales: `S12-Sep08/DData/chery.dbf`, `M11/DData/chery.dbf` y `S21-Sep08/DData/chery.dbf`.
- [x] Confirmar las carpetas de ilustraciones y su capitalización: S12 usa `pictures/`; M11 y S21 usan `Pictures/`.
- [x] Añadir configuración Gradle para abrir `AndroidApp/` como proyecto Android Studio.
- [x] Añadir pantalla principal Compose con selector de vehículo, búsqueda, recarga, estado de carga y errores.
- [x] Añadir lector Kotlin que descarga el DBF por HTTPS y conserva todas las columnas.
- [x] Etiquetar cada registro por carpeta de origen y filtrar antes de buscar.
- [x] Añadir selector de idioma español/inglés/ruso con preferencia persistente.
- [ ] Validar los nombres de columnas reales de los tres DBF; los alias usados por ahora son heurísticos.
- [ ] Probar descarga y parseo de cada DBF en dispositivo/emulador con Android Studio.
- [ ] Conectar y probar las ilustraciones por vehículo.
- [ ] Compilar y generar APK firmado/de prueba.

Solo se leen los archivos `chery.dbf`, no tablas de cuentas/usuarios. Se requiere Internet. No afirmar que hay APK listo hasta compilar y probar.
