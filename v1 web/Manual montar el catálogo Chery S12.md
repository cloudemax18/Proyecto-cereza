# Manual: montar el catálogo Chery S12

Sitio: https://chinocatalogo.xo.je · Versión del 15 de octubre de 2026

## 1. Qué incluye el ZIP

- **catalogo-chery/**: el sitio web. Esto es lo único que se sube a InfinityFree.
- **importar/nodos.csv** y **importar/piezas.csv**: los datos del catálogo (138 grupos y 1.690 piezas) para cargar en Supabase.
- **importar/fotos/**: 3.885 fotos ya comprimidas, para subir al almacén privado de Supabase. No se suben a InfinityFree.
- **extras/htaccess.txt**: copia visible del archivo `.htaccess`, por si tu programa de ZIP oculta los archivos que empiezan por punto.

Idea general: InfinityFree sirve las páginas, y Supabase guarda las cuentas, los datos y las fotos. Solo quien inicia sesión puede ver el catálogo.

Supabase ya quedó preparado en tu proyecto "Proyecto cereza": tablas, reglas de acceso, almacén privado de fotos y verificación de edad. Lo que falta es cargar los datos y las fotos.

## 2. Completa tus datos en las páginas

Abre cada archivo con un editor de texto (Bloc de notas++, VS Code o similar, guardando en UTF-8) y reemplaza los textos entre corchetes. Cada uno aparece resaltado dentro de una etiqueta `<mark>`; puedes borrar la etiqueta y dejar solo tu texto.

- **privacidad.html**: `[NOMBRE O RAZÓN SOCIAL]` y `[CORREO DE CONTACTO]`.
- **terminos.html**: los mismos dos, más `[PAÍS / JURISDICCIÓN]`.
- **dmca.html**: `[NOMBRE LEGAL O RAZÓN SOCIAL]`, `[NOMBRE Y APELLIDO]`, `[CALLE, CIUDAD, PAÍS, CÓDIGO POSTAL]`, `[CORREO DE CONTACTO]` y `[TELÉFONO]`. La dirección quedará pública: si no quieres poner la de tu casa, usa un apartado postal o un agente registrado.
- **index.html**: busca `data-coffee="https://www.buymeacoffee.com/TU_USUARIO"` y cambia la dirección por tu enlace real de "invítame un café". Debe empezar por `https://`.

**Importante:** en `index.html` no modifiques nada dentro de la etiqueta `<script>`. La página tiene una protección que la bloquea si ese código cambia, y quedaría en blanco. Si necesitas otros cambios, pídemelos y regenero los archivos.

## 3. Supabase: cargar los datos

Entra en https://supabase.com/dashboard y abre el proyecto **Proyecto cereza**. Los nombres exactos de los menús pueden variar un poco.

1. Ve a **Table Editor** y abre la tabla **nodos**.
2. Usa la opción de insertar e importar datos desde un archivo CSV y elige `importar/nodos.csv`. Comprueba que las columnas coincidan (id, nombre, padre, nfotos) y confirma.
3. Repite con la tabla **piezas** y el archivo `importar/piezas.csv`. Importa siempre primero nodos y después piezas.
4. Comprueba los totales: **138** filas en nodos y **1.690** en piezas.

## 4. Supabase: subir las fotos

1. Ve a **Storage** y abre el almacén **fotos** (es privado; así debe quedar).
2. Sube los archivos de `importar/fotos/` directamente en la raíz del almacén, sin crear carpetas. Los nombres deben quedar tal cual (por ejemplo `S12-3403022.jpg` o `S12-DQ.jpg`).
3. Son 3.885 archivos. Si el navegador se traba, súbelos por tandas de unos 500.
4. Al terminar, comprueba que el recuento sea cercano a 3.885.

Nota: el archivo `S12-BSXZC.fla.jpg` es un sobrante del original; puedes omitirlo o dejarlo.

## 5. Supabase: configurar el acceso

En **Authentication**:

1. **URL Configuration**: pon `https://chinocatalogo.xo.je` como Site URL y añade `https://chinocatalogo.xo.je/gracias.html` en Redirect URLs. Sin esto, el enlace del correo de confirmación no llevará a la página de gracias.
2. Deja activada la confirmación de correo.
3. **SMTP**: el envío de correos por defecto de Supabase tiene límites muy bajos (muy pocos correos por hora). Para recibir registros reales, configura un servicio de correo propio (SMTP).
4. En las opciones de contraseñas, sube la longitud mínima a 8 o más y activa la protección contra contraseñas filtradas si tu plan la ofrece.
5. Revisa los límites de intentos (rate limits) y déjalos en valores conservadores.
6. **No** desactives el registro de nuevos usuarios: los visitantes necesitan poder crear su cuenta.
7. El CAPTCHA (Cloudflare Turnstile) no está activo. Activarlo exige regenerar la página, porque cambia su política de seguridad; pídemelo cuando lo quieras.

## 6. InfinityFree: subir el sitio

1. Entra al panel de InfinityFree, abre tu cuenta de hosting `chinocatalogo.xo.je` y entra al **Online File Manager** (o conéctate por FTP con FileZilla usando los datos de la cuenta).
2. Abre la carpeta **htdocs**. Si hay un archivo de bienvenida del proveedor (por ejemplo `index2.html`), bórralo.
3. Sube **el contenido** de `catalogo-chery/` dentro de htdocs, no la carpeta en sí. Deben quedar en htdocs: `index.html`, `privacidad.html`, `terminos.html`, `dmca.html`, `404.html`, `gracias.html`, `sitemap.xml`, `robots.txt`, `favicon.svg`, `favicon.ico`, `apple-touch-icon.png`, `og-image.png` y `.htaccess`.
4. Si no ves `.htaccess`, activa "mostrar archivos ocultos", o sube `extras/htaccess.txt` y renómbralo a `.htaccess`.
5. Límites de InfinityFree a tener presentes: 10 MB por archivo, 1 MB para HTML y PHP, 10 KB para `.htaccess` y 30.000 archivos en total. Este sitio cabe de sobra.
6. Abre http://chinocatalogo.xo.je y comprueba que aparece la pantalla de entrada con el anuncio del café.

## 7. InfinityFree: activar HTTPS

1. En tu panel de cliente, solicita el **certificado SSL gratuito** para el dominio y sigue sus pasos de verificación e instalación. Si tu dominio ya abre con `https://`, salta este paso.
2. Cuando `https://chinocatalogo.xo.je` abra sin avisos, edita `.htaccess` y quita el `#` de estas tres líneas: `RewriteEngine On`, `RewriteCond %{HTTPS} off` y `RewriteRule ^(.*)$ https://%{HTTP_HOST}/$1 [R=301,L]`.
3. No lo actives antes de que el certificado funcione: el sitio dejaría de abrir.

## 8. Crear tu cuenta de administrador

1. Abre el sitio, pulsa **Crear cuenta**, escribe correo, contraseña de 8 o más caracteres y tu fecha de nacimiento, y acepta los términos.
2. Confirma el correo con el enlace que recibas; debe llevarte a la página de gracias.
3. Entra con tu cuenta y pulsa **Soy el administrador**. Hazlo tú primero: la primera cuenta que lo pulse queda como administradora y nadie más puede reclamarlo después.
4. Verás nuevos controles: **Estadísticas** (contadores anónimos), el filtro **Solo sin foto** y botones **＋** para subir fotos que falten a una pieza o grupo.

## 9. Lista de pruebas finales

- Un visitante sin cuenta ve la pantalla de entrada y el anuncio del café, pero no el catálogo.
- Intentar registrarse con una fecha de nacimiento de menor de 13 años muestra un error.
- Tras entrar se cargan los 138 grupos y se ven las fotos de las piezas.
- Una dirección inexistente, como `/prueba`, muestra la página 404.
- Al ver el código fuente de la página (clic derecho, ver código), no aparecen datos del catálogo.
- Pega el enlace en WhatsApp para ver la vista previa. InfinityFree a veces bloquea esas visitas; si falla, es una limitación del servicio gratuito.
- Prueba https://securityheaders.com con tu dirección para revisar las cabeceras de seguridad.

## 10. Después de publicar

**Registrar el agente DMCA** (recomendado si recibes contenido de otras personas y para dar un contacto de derechos de autor):

1. Entra en dmca.copyright.gov y crea una cuenta.
2. Registra el servicio con el nombre del proveedor, la dirección y el agente designado, idénticos a los de `dmca.html`.
3. Paga la tarifa (alrededor de 6 USD).
4. Renueva el registro cada 3 años.

**Buscadores:** en Google Search Console añade tu dominio y envía `https://chinocatalogo.xo.je/sitemap.xml`. Solo las páginas públicas se indexan; el catálogo queda tras el registro.

## 11. Mantenimiento

- **Corregir o añadir datos:** edítalos en Table Editor de Supabase.
- **Fotos que faltan:** súbelas desde el sitio como administrador (se reducen automáticamente a 1024 px).
- **Copias de seguridad:** exporta las tablas a CSV de vez en cuando y conserva tu copia de las fotos originales.
- **Cambios en el diseño o los textos:** pídelos y regenero los archivos.

## 12. Si algo falla

- **Página en blanco:** probablemente se modificó el bloque `<script>` de `index.html`. Vuelve a subir el original del ZIP.
- **"No se pudo cargar el catálogo":** las tablas están vacías o la sesión venció. Revisa el paso 3 y vuelve a entrar.
- **No se ven las fotos:** revisa que estén en la raíz del almacén `fotos` y con los nombres originales.
- **No llegan los correos:** configura SMTP propio (paso 5).
- **El enlace del correo no lleva a la página de gracias:** revisa las direcciones del paso 5.
- **Error al registrar:** puede ser la edad mínima de 13 años o un correo ya usado.
- **Sitio inaccesible tras editar `.htaccess`:** vuelve a poner el `#` en las tres líneas de HTTPS o sube de nuevo el original.

## 13. Riesgos y advertencias

- Tu repositorio de GitHub es público y contiene `pictures.zip` y las partes `.z01` a `.z32`: las fotos siguen accesibles ahí. Hazlo privado o borra esos archivos si quieres que solo las vean los registrados.
- No compartas la primera versión publicada en Claude (el artefacto con todos los datos dentro del HTML): no tiene control de acceso.
- Los datos y las fotos del catálogo son de Chery o de terceros. Confirma que tienes permiso para publicarlos. Este manual no es asesoría legal.
- Las estadísticas son anónimas y sin cookies, pero cualquiera puede inflar los contadores.
- Sitio gratuito: InfinityFree tiene límites de recursos y política de inactividad; si el catálogo crece, considera un plan de pago.
