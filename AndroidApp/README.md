# Chery SPMS Android — catálogo multimodelo

Esta carpeta define la integración Android prevista para los tres modelos del repositorio. No mueve, renombra ni elimina los datos existentes.

## Modelos

| ID interno | Carpeta de origen | Datos |
|---|---|---|
| `s12` | `S12-Sep08/` | DBF y `pictures/` |
| `m11` | `m11/` | DBF y `pictures/` |
| `s21` | `S21-Sep08/` | DBF y `pictures/` |

## Comportamiento del catálogo

1. El usuario elige un vehículo.
2. La aplicación guarda el ID del vehículo seleccionado.
3. La búsqueda y la lista muestran únicamente piezas cuyo `vehicleId` coincide con la selección.
4. Los diagramas se consultan dentro de la carpeta `pictures/` del vehículo seleccionado, para evitar mezclar ilustraciones entre modelos.
5. El idioma de la interfaz se puede elegir entre español, inglés y ruso. Los códigos y nombres originales de piezas se conservan como están en los DBF.

## Formato normalizado para el catálogo

Cada registro convertido desde el DBF debe conservar sus campos originales y añadir `vehicleId` con uno de estos valores: `s12`, `m11` o `s21`.

```json
{
  "vehicleId": "s12",
  "serial": "CODIGO-DE-PIEZA",
  "name": "NOMBRE ORIGINAL",
  "model": "MODELO-DEL-DBF",
  "stock": 1,
  "location": "UBICACION-ORIGINAL"
}
```

## Estado y limitaciones

Este directorio documenta el esquema y la regla de filtrado multimodelo. Antes de publicar una versión instalable, aún se deben conectar los nombres reales de los DBF de `m11/` y `S21-Sep08/`, importar sus registros y verificar el visor de ilustraciones en Android. Los archivos de ilustraciones del repositorio pueden ser SWF/Flash aunque tengan extensión `.mjs`; Android no los muestra de forma nativa.
