# Guion de la demo

Presentación de 10 minutos, minuto a minuto, a partir de los escenarios de la tabla 2.13 de `AGENTS.md`. Funciona igual con `LLM_PROVIDER=mock` y con un LLM real; con el LLM real solo cambia la redacción de las justificaciones.

## Preparación

- [ ] `docker compose up -d --build` (o backend, partners-mock y frontend por separado, ver `README.md`)
- [ ] En **Simulación**, pulsar **Reiniciar demo** (equivale a `POST /api/v1/simulation/reset`). Deja el reloj en el día 1 a las 00:00, en pausa.
- [ ] Tres ventanas con sesión abierta: `admin@laceiba.demo` (simulación), `gerencia@laceiba.demo` (aprobaciones grandes y traslados) y `compras@laceiba.demo` (aprobaciones medianas). Contraseña: `Xperp2026*`.
- [ ] Una ventana más, en modo celular, con `bodega.cali@laceiba.demo` en **Bodega**.
- [ ] Mailpit abierto en http://localhost:8025.
- [ ] Velocidad: 20 s por día simulado. Para saltar días enteros se usa **Avanzar horas** con el reloj en pausa.

### Sobre las aprobaciones

Con `SIM_AUTO_APPROVE=false` (el valor por defecto), las OC de $ 5.000.000 o más, los traslados y los ajustes esperan a una persona. Si nadie las aprueba, vencen a las 24 horas simuladas y la OC se cancela. Por eso, si se deja correr el mes completo sin nadie, el nivel de servicio cae a cerca del 45 %. En la presentación las aprobaciones se hacen en vivo.

Para ensayar el mes completo sin personas, se levanta el backend con `SIM_AUTO_APPROVE=true`. En el ensayo del 2026-10-23 con semilla 42, el mes cerró con un nivel de servicio del 99,8 %, 0 SKU en quiebre, una ejecución de presupuesto del 77,2 % y un sobrecosto por reasignaciones de $ 7,7 M.

En el ensayo del 2026-10-30, también con semilla 42 y en modo `mock`, se revisaron en la base de datos los 14 escenarios de la tabla 2.13, y se cumplen todos. El mes cerró con un nivel de servicio del 99,7 %, una ejecución de presupuesto del 77,5 %, 314 unidades perdidas y un sobrecosto por reasignaciones de $ 7,2 M. Falta el ensayo con un LLM real, que necesita una llave de API.

## Guion

| Minuto | Qué se muestra | Escenario (tabla 2.13) | Pantalla |
|---|---|---|---|
| 0–1 | Problema y propuesta: un ERP donde la IA compra, avisa a bodega y despacha, y deja registro de cada decisión. Barra superior con reloj simulado y modo de la IA. | | Tablero |
| 1–2 | **Iniciar** la simulación. Entran pedidos en vivo y, a las 06:00, el ciclo diario genera OC: la leche va a PRV-03 por el tiempo de entrega y el aceite a PRV-01. | Día 1, aceite y leche | Simulación → Tablero |
| 2–3 | Abrir la decisión de la leche: qué datos vio, las alternativas con su puntaje, por qué eligió esa y el resultado de la política. Mostrar el correo con el PDF en Mailpit. | Día 1 | IA → Decisiones, Mailpit |
| 3–4 | Pañales en 0 en BOG-01: la IA propone un traslado desde CAL-01 en vez de comprar. Gerencia lo aprueba. | Día 1, pañales | IA → Aprobaciones (gerencia) |
| 4–5 | El bodeguero inicia y termina una tarea desde el celular. El pedido queda empacado y la IA elige transportadora y genera la guía. | Flujo de despacho | Bodega (celular), Despachos |
| 5–6 | Pausar y avanzar hasta el día 5: PRV-06 retrasa el papel higiénico 4 días. La IA reasigna a PRV-09 y explica el sobrecosto. Compras la aprueba. | Días 1 y 5 | Compras, IA → Aprobaciones |
| 6–7 | Avanzar al día 6: PRV-02 rechaza el arroz y la IA reasigna a PRV-01 sin pedir aprobación (sobrecosto ≤ 10 %). Al día 10, alerta por el alza del café y nueva calificación de proveedores. | Días 6 y 10 | IA → Decisiones, Tablero (alertas) |
| 7–8 | Avanzar al día 15: quincena (pico de ventas) y discrepancia en el conteo del detergente. Se ven la alerta, la tarea de reconteo y el ajuste pendiente de gerencia. | Día 15 | Inventario (kardex), IA → Aprobaciones |
| 8–9 | Avanzar al día 20: el pedido anómalo de azúcar queda retenido; se libera desde Ventas. Al día 22, el presupuesto de CPE pasa el 85 % y la IA prioriza por margen y cobertura. Caramelos: promoción sugerida y recompra bloqueada. | Días 20 y 22, todo el mes | Ventas, Tablero (presupuesto), Inventario |
| 9–10 | Controles: modo sombra y kill switch en **IA → Políticas**, y revertir una decisión. Cierre con el asistente: "¿qué se agota esta semana?" y "¿por qué se le compró a Mayorista Central?". | Día 30, cierre | IA → Políticas, Asistente |

## Plan B

- Si el LLM real falla o tarda, se cambia a `LLM_PROVIDER=mock` y se reinicia el backend: el guion es el mismo.
- Si se pierde el hilo, se pulsa **Reiniciar demo** y se usa **Avanzar horas** para volver al día en que se iba.
