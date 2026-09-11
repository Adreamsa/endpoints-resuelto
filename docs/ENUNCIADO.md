# Ejercicio: API de Cotización de Envíos

## Contexto

El equipo de logística necesita un servicio backend que permita a los clientes obtener una cotización de envío antes de confirmar un pedido. El sistema debe recibir la información del paquete, validarla, y devolver el costo estimado junto con el tiempo de entrega. Si algo no es válido, el cliente debe recibir una respuesta clara indicando qué salió mal.

Además, el equipo de frontend necesita poder mostrar en un selector las zonas de destino que el sistema soporta actualmente.

## Requerimientos funcionales

### 1. Cotizar envío

El cliente envía una solicitud con:

- Origen
- Destino
- Peso del paquete (en kg)

El sistema debe:

- Validar que los datos vengan completos y sean coherentes.
- Calcular un costo estimado y un tiempo de entrega según el destino y el peso.
- Si el destino no está soportado, o el paquete excede el peso máximo permitido, informar al cliente de forma clara.

**Reglas de negocio:**

- El peso máximo permitido para cualquier envío es de **50 kg**.
- Para paquetes de hasta **70 kg**, se aplica una tarifa especial de sobrepeso en lugar de rechazar el envío.
- El costo base depende de la zona de destino (tú defines la tabla de tarifas; documenta tus valores y el criterio usado).

### 2. Consultar zonas soportadas

El sistema debe exponer las zonas de destino válidas actualmente, para que puedan mostrarse al cliente antes de que intente cotizar.

## Restricciones técnicas

- Java + Spring Boot.
- Máximo **2 endpoints**. No se requiere ni se espera persistencia de ningún tipo (sin base de datos, sin estado en memoria entre requests).
- Usa Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, etc.) para las validaciones de entrada. No se requieren capas de abstracción adicionales (no repository, no service con estado) — mantén la solución simple y directa.
- Usa códigos de estado HTTP semánticamente correctos (no todo debe responder `200`).
- El formato exacto de las respuestas (éxito y error) queda a tu criterio. Documenta las decisiones que tomaste.

## Entregable

- Código fuente del proyecto Spring Boot.
- Un `README.md` breve donde documentes:
  - Cualquier supuesto que hayas tenido que asumir por falta de información en este enunciado.
  - Tu tabla de tarifas y el criterio usado para definirla.
  - Cualquier inconsistencia que hayas notado en este documento y cómo decidiste resolverla.

## Criterios de evaluación

- Corrección de las validaciones y manejo de errores.
- Uso adecuado de códigos HTTP.
- Claridad y calidad del contrato REST (request/response).
- Calidad de las decisiones documentadas ante la ambigüedad — **no se penaliza interpretar el requerimiento de una forma u otra, se penaliza no declarar que hubo una interpretación.**
