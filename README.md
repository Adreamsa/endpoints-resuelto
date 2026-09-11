# API de Cotización de Envíos

Servicio de cotización de envíos: recibe origen, destino y peso, y devuelve el costo estimado
y el tiempo de entrega. El enunciado completo está en [`docs/ENUNCIADO.md`](docs/ENUNCIADO.md).

El código, las rutas y los mensajes de la API están en inglés; esta documentación está en
español.

---

## Cómo correr

Requiere **JDK 21**.

```bash
make build   # compila y empaqueta el jar en target/ (incluye tests)
make run     # levanta la aplicación en http://localhost:8080 (Ctrl+C para detener)
make test    # corre la suite de tests
make clean   # borra target/
```

**¿No tienes `make`?** Cada target es una sola línea de Maven:

| Target | Linux / macOS / Git Bash | Windows (CMD / PowerShell) |
|---|---|---|
| `make build` | `./mvnw clean install` | `mvnw.cmd clean install` |
| `make run` | `./mvnw spring-boot:run` | `mvnw.cmd spring-boot:run` |
| `make test` | `./mvnw test` | `mvnw.cmd test` |
| `make clean` | `./mvnw clean` | `mvnw.cmd clean` |

Hay peticiones listas para ejecutar en [`docs/peticiones.http`](docs/peticiones.http)
(IntelliJ IDEA, o VS Code con la extensión REST Client).

---

## Endpoints implementados

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/zones` | Catálogo de zonas soportadas, con sus tarifas y tiempos de entrega |
| `POST` | `/api/v1/quotes` | Cotiza un envío a partir de origen, destino y peso |

### `GET /api/v1/zones` → `200 OK`

```json
[
  {
    "code": "METROPOLITAN",
    "displayName": "Metropolitan area",
    "description": "Same-city deliveries inside the metropolitan area.",
    "baseCost": 90.00,
    "costPerKg": 12.00,
    "estimatedBusinessDays": 1
  }
]
```

### `POST /api/v1/quotes` → `200 OK`

Petición:

```json
{
  "origin": "METROPOLITAN",
  "destination": "NATIONAL_EXTENDED",
  "weightKg": 55.00
}
```

Respuesta:

```json
{
  "origin": "METROPOLITAN",
  "destination": "NATIONAL_EXTENDED",
  "weightKg": 55.00,
  "currency": "MXN",
  "overweight": true,
  "costBreakdown": {
    "baseCost": 190.00,
    "weightCost": 1375.00,
    "subtotal": 1565.00,
    "overweightSurcharge": 626.00
  },
  "totalCost": 2191.00,
  "delivery": {
    "businessDays": 5,
    "description": "Estimated delivery in 5 business days. Includes 1 extra business day for special overweight handling."
  }
}
```

---

## Estructura del proyecto

```
src/main/java/com/teletubies/endpoints/
├── EndpointsApplication.java
│
├── enums/
│   └── Zone.java                        catálogo de zonas + tabla de tarifas
│
├── exception/                           transversal a toda la API
│   ├── ApiErrorResponse.java            contrato ÚNICO de error
│   ├── ApiExceptionHandler.java         @RestControllerAdvice global
│   ├── UnsupportedZoneException.java
│   ├── WeightLimitExceededException.java
│   └── InvalidRouteException.java
│
└── shipping/
    ├── controller/ShippingQuoteController.java    los 2 endpoints
    ├── service/ShippingQuoteService.java          reglas de negocio, sin estado
    └── dto/                                       contratos de entrada y salida
```

Las piezas transversales (contrato de error, handler, excepciones de negocio) viven en el
paquete raíz porque no pertenecen a un endpoint en particular: el handler es global y el
contrato de error es el mismo para toda la API. Lo específico del dominio de envíos vive
bajo `shipping/`.

No hay repository ni service con estado: el servicio es un singleton sin atributos mutables
y el catálogo es una constante de compilación (un `enum`), no persistencia.

---

## Decisiones de diseño

**El catálogo es un `enum`, no una lista de strings.** El endpoint de zonas y la validación
del POST leen de la misma fuente, así que no pueden contradecirse. Agregar una zona es tocar
un solo archivo.

**`GET /zones` devuelve tarifas, no solo etiquetas.** El enunciado pide "mostrar las zonas en
un selector", pero un front que solo recibe códigos tiene que hardcodear sus propias etiquetas
y ahí es donde se desincroniza. Devolviendo `baseCost`, `costPerKg` y `estimatedBusinessDays`,
la UI puede previsualizar una zona sin ir y volver al POST.

**La cotización responde `200`, no `201`.** El POST calcula sobre lo que le mandaron; no crea
ningún recurso, así que no habría `Location` que devolver.

**La respuesta trae el desglose, no solo el total.** `costBreakdown` deja ver de dónde salió
cada peso. Sin eso, el front que quiera mostrar "base + peso + recargo" tiene que reimplementar
la aritmética y el redondeo, y puede llegar a un número distinto al nuestro.

**`overweight` es un campo explícito.** Que el envío cayó en sobrepeso es información que el
cliente necesita para advertirle al usuario, y no debería tener que inferirla comparando
`overweightSurcharge` contra cero.

**Los campos `origin` y `destination` se reciben como `String`, no como `enum`.** Si se
declararan como `Zone`, un código inválido lo rechazaría Jackson durante la deserialización y
saldría un `400` genérico. Recibiéndolos como texto, la resolución del catálogo es una regla de
negocio que responde `422` con un mensaje que dice cuál zona falló y en qué campo.

**El dinero es `BigDecimal`, escala 2, `HALF_UP`.** Fijo para todos los cálculos del servicio.
Sin una regla única, dos operaciones del mismo sistema redondean distinto y no cuadran por un
centavo.

---

## Supuestos asumidos

El enunciado deja varias cosas abiertas. Esto es lo que decidí y con qué criterio:

| Supuesto | Decisión | Criterio |
|---|---|---|
| ¿Origen y destino son texto libre o códigos? | Códigos de un catálogo cerrado, el mismo para ambos campos | Un selector necesita un catálogo. Texto libre haría imposible cotizar de forma determinista |
| ¿El peso admite decimales? | Sí, máximo 2 decimales (`@Digits(integer = 3, fraction = 2)`) | Dos decimales es precisión de 10 gramos, suficiente para paquetería. Más decimales sería precisión falsa |
| ¿Peso 0 o negativo? | Rechazado con `400` (`@DecimalMin("0.01")`) | Un paquete de 0 kg no existe. Es un error de formato, no de negocio |
| ¿Origen y destino pueden ser iguales? | No, `422 ORIGIN_EQUALS_DESTINATION` | Un envío dentro de la misma zona no es una ruta; si en el futuro existe ese servicio, tendría su propia tarifa |
| ¿En qué moneda se cotiza? | MXN fijo, declarado en el campo `currency` de la respuesta | El enunciado no menciona multi-moneda. El campo está en el contrato para no romperlo si algún día se agrega |
| ¿El tiempo de entrega es una fecha? | No: días hábiles (`businessDays`) | Devolver una fecha exigiría calendario de días festivos y hora de corte, que este servicio no tiene |
| ¿El origen afecta el precio? | No (ver inconsistencias) | — |
| ¿Se versiona la API? | Sí, prefijo `/api/v1` | La tabla de tarifas va a cambiar; el prefijo deja lugar para hacerlo sin romper clientes |

---

## Tabla de tarifas

Fórmula: **`subtotal = baseCost + (weightKg × costPerKg)`**, más el recargo por sobrepeso
cuando aplica. Todos los valores en MXN.

| Zona (`code`) | Costo base | Costo por kg | Días hábiles |
|---|---|---|---|
| `METROPOLITAN` | 90.00 | 12.00 | 1 |
| `NATIONAL_CENTRAL` | 140.00 | 18.00 | 2 |
| `NATIONAL_EXTENDED` | 190.00 | 25.00 | 4 |
| `INTERNATIONAL_NA` | 450.00 | 60.00 | 7 |

**Criterio usado.** Los números son inventados —el enunciado pide inventarlos— pero no son
arbitrarios; siguen tres reglas:

1. **El costo base cubre recolección y última milla**, que es costo fijo por envío: existe
   aunque el paquete pese 100 gramos. Crece con la complejidad logística de la zona (más
   puntos de trasbordo, menor densidad de entregas), no linealmente con la distancia. Por eso
   el salto grande está entre nacional e internacional (aduana, transportista socio), no entre
   las dos zonas nacionales.
2. **El costo por kg cubre el flete variable**, y escala con el mismo criterio: mover un kilo
   más cuesta poco en una ciudad y mucho cruzando una frontera.
3. **Los días hábiles son el SLA comprometido**, no el tiempo esperado: es el número que se le
   promete al cliente, con holgura ya incluida.

Las tarifas viven en `Zone.java`. Agregar o reajustar una zona es editar ese archivo; el
endpoint de catálogo y la validación del POST se actualizan solos.

### Recargo por sobrepeso

**40% sobre el subtotal, más 1 día hábil adicional.** El criterio: un paquete de más de 50 kg
deja de ser manejable por una persona y requiere equipo y maniobra especial. Ese costo es
proporcional al envío completo (un paquete grande *y* lejano es más caro de maniobrar que uno
grande y cercano), por eso es un porcentaje del subtotal y no una cuota fija. El día extra es
el tiempo de la maniobra especial, que no pasa por la ruta normal.

---

## Contrato de errores

**Un solo formato para toda la API**, idéntico en los dos endpoints:

```json
{
  "timestamp": "2026-09-11T01:38:22.404-06:00",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "One or more fields are invalid",
  "details": [
    { "field": "destination", "message": "Destination zone code is required" },
    { "field": "weightKg", "message": "Package weight must be greater than 0 kg" }
  ]
}
```

- **`code`** es el discriminante legible por máquina; el cliente ramifica sobre él, no sobre
  el texto de `message`, que puede cambiar o traducirse.
- **`details`** lleva los problemas por campo y **se omite cuando está vacío**
  (`@JsonInclude(NON_EMPTY)`), para no obligar a los errores de negocio a inventar un nombre
  de campo.
- **Se reportan *todos* los campos inválidos a la vez**, ordenados por nombre. Devolver solo
  el primero deja al cliente corrigiendo su petición de una en una.
- **No se elige RFC 7807 / `ProblemDetail`.** Es una alternativa defendible, pero su campo
  `type` es una URI que debe apuntar a documentación publicada, y aquí no existe tal sitio.
  El formato propio da lo que el front necesita —un código estable y el detalle por campo—
  sin prometer algo que no se puede cumplir.

### Códigos HTTP

| Situación | Código | `code` |
|---|---|---|
| Campo ausente, vacío o fuera de rango | `400` | `VALIDATION_FAILED` |
| JSON roto o cuerpo ausente | `400` | `MALFORMED_REQUEST` |
| La URL no existe | `404` | `RESOURCE_NOT_FOUND` |
| Método no soportado en una URL válida | `405` | `METHOD_NOT_ALLOWED` |
| `Content-Type` distinto de `application/json` | `415` | `UNSUPPORTED_MEDIA_TYPE` |
| Zona de origen o destino fuera del catálogo | `422` | `UNSUPPORTED_ZONE` |
| Peso mayor a 70 kg | `422` | `WEIGHT_LIMIT_EXCEEDED` |
| Origen igual a destino | `422` | `ORIGIN_EQUALS_DESTINATION` |
| Falla inesperada del servidor | `500` | `INTERNAL_ERROR` |

**El criterio `400` vs `422`:**

- `400` — la petición está mal **formada**. El servidor no pudo entenderla: JSON roto, un
  campo obligatorio ausente, un número fuera del rango declarado.
- `422` — la petición está bien formada y se entendió perfectamente, pero su contenido
  incumple una regla de **negocio**. `"NATIONAL_CENTRAL"` y `"MARS"` son igual de válidos
  sintácticamente; solo el dominio sabe que uno no existe.

**Sobre el `404`:** se usa únicamente para decir "esa URL no existe", nunca para una condición
de negocio. Una zona no soportada **no** es `404`: el recurso `/api/v1/quotes` existe y
respondió; lo que falla es el contenido. Ninguno de los dos endpoints resuelve un recurso por
identificador, así que el `404` de "recurso no encontrado" no tiene dónde aparecer aquí.

**Lo que no se hace:** responder `200 OK` con un `"success": false` en el cuerpo. El código de
estado es parte de la respuesta, no un adorno.

**Sobre el manejo de excepciones de Spring:** el handler atrapa explícitamente
`NoResourceFoundException`, `HttpRequestMethodNotSupportedException` y
`HttpMediaTypeNotSupportedException` además de las de negocio. Sin eso, el handler genérico de
`Exception` las convertiría en `500` y un simple typo en la URL se reportaría como una falla
del servidor.

---

## Inconsistencias detectadas en el enunciado

### 1. Los dos límites de peso se contradicen

El enunciado dice, en dos líneas consecutivas:

> El peso máximo permitido para cualquier envío es de **50 kg**.

> Para paquetes de hasta **70 kg**, se aplica una tarifa especial de sobrepeso en lugar de
> rechazar el envío.

Las dos reglas no pueden ser ciertas a la vez: si 50 kg es un tope duro, la segunda regla es
inalcanzable —nunca habría un paquete entre 50 y 70 kg que cotizar—. Y si se acepta hasta 70 kg,
entonces 50 kg no es el máximo.

**Interpretación elegida:** *50 kg es el límite de la **tarifa estándar**; 70 kg es el límite
**físico** del servicio.* La segunda regla es más específica que la primera y es la única que
describe un comportamiento ("se aplica una tarifa especial... en lugar de rechazar"), así que
manda sobre la redacción genérica de la primera.

| Peso | Comportamiento | Respuesta |
|---|---|---|
| `0 < p ≤ 50 kg` | Tarifa estándar | `200`, `overweight: false` |
| `50 < p ≤ 70 kg` | Tarifa estándar + 40% de recargo, +1 día hábil | `200`, `overweight: true` |
| `p > 70 kg` | Rechazado | `422 WEIGHT_LIMIT_EXCEEDED` |

Consecuencia de diseño: **el rechazo por peso no es un `@Max` en el DTO.** Un `@Max(70)`
respondería `400` con un mensaje de campo fuera de rango, cuando lo que realmente pasa es que
el envío es válido pero el servicio no lo puede mover. El límite vive en el servicio y responde
`422` explicando cuál es el máximo. Las fronteras exactas (50.00, 50.01, 70.00, 70.01) están
cubiertas por tests.

### 2. Se pide `origen`, pero ninguna regla lo usa

El enunciado pide recibir origen y destino, pero luego especifica que *"el costo base depende de
la zona de destino"* y que el tiempo de entrega se calcula *"según el destino y el peso"*. Con
eso, el origen no entra en ningún cálculo.

**Decisión:** el origen se recibe y se **valida** (debe ser una zona soportada y debe ser
distinto del destino), pero **no afecta el precio**. No inventé una matriz origen×destino
porque el enunciado dice explícitamente que el costo depende del destino, y sustituir eso por
una regla propia sería resolver un ejercicio distinto al que se pidió.

Si el origen debiera influir en la tarifa, el cambio está acotado a `ShippingQuoteService`: la
resolución de tarifas pasaría de leer `destination` a leer el par.

### 3. "Máximo 2 endpoints" vs. el manejo de errores

El límite de 2 endpoints se respeta: hay exactamente dos rutas mapeadas
(`GET /api/v1/zones` y `POST /api/v1/quotes`). El `@RestControllerAdvice` no agrega rutas —no
tiene `@RequestMapping`—, solo traduce excepciones de las existentes. Lo anoto por si el
conteo se hace por archivos en vez de por rutas.

---

## Tests

`make test` — 23 tests, sin dependencias externas.

- **`ShippingQuoteServiceTest`** — reglas de negocio con JUnit puro, sin levantar Spring:
  fórmula de costo, fronteras de peso (50.00 / 50.01 / 70.00 / 70.01), normalización de
  códigos de zona, y cada regla que produce un rechazo.
- **`ShippingQuoteControllerTest`** — contrato REST con `@WebMvcTest`: un caso por código de
  estado, la forma del cuerpo de error, y que `details` reporte los tres campos inválidos de
  una petición en una sola respuesta.
