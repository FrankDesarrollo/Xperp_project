# Xperp: contexto del proyecto

Xperp (Expert ERP) es un SaaS de gestión empresarial en el que la IA toma decisiones operativas, las ejecuta y les hace seguimiento. Además de analizar datos o responder preguntas, la IA actúa. El caso de uso es una distribuidora de productos de consumo masivo. Cuando las ventas bajan el inventario, el sistema calcula cuánto comprar, elige proveedor, envía la orden, avisa a bodega qué preparar, genera la guía de despacho y deja registro de por qué tomó cada decisión.

Es un proyecto académico de dos personas, con datos demo. La arquitectura, en cambio, debe permitir pasar de MVP a producto comercial sin reescribirlo.

Este archivo es la fuente de contexto para el equipo y para cualquier agente de IA que trabaje en el repositorio. Si el código y este documento se contradicen, se corrige uno de los dos en el mismo PR.

---

## 1. Principios del producto

1. La IA es parte del flujo. Si se apaga, el ERP sigue funcionando, pero alguien tiene que hacer a mano lo que ella hacía.
2. Cada decisión es trazable: qué datos vio, qué alternativas tenía, qué eligió, por qué y quién la aprobó.
3. La IA propone, el backend valida y la política decide. El LLM nunca toca la base de datos.
4. Hay supervisión humana configurable: niveles de autonomía, umbrales de monto, kill switch y modo sombra.
5. El sistema arranca con una empresa demo completa y opera solo. Nadie tiene que cargar datos para que la demo funcione.
6. Las reglas determinísticas resuelven lo que tiene respuesta exacta. El LLM se usa cuando hay que elegir entre opciones, manejar excepciones o explicar.

---

## 2. Empresa demo

Todos los nombres de empresas, personas, proveedores y transportadoras son ficticios. No se usan marcas reales en productos ni en socios.

### 2.1 Datos generales

| Campo | Valor |
|---|---|
| Razón social | Distribuidora La Ceiba S.A.S. |
| NIT de prueba | 999999999-1 |
| Actividad | Distribución mayorista de productos de consumo masivo |
| Sede | Cali, Valle del Cauca |
| Moneda | COP |
| Zona horaria | America/Bogota |
| Dominio de correos demo | laceiba.demo |
| Inicio de la simulación | 2026-11-01 (día 1) |
| Historial previo generado | 90 días (2026-08-03 a 2026-10-31) |

### 2.2 Bodegas

| Código | Nombre | Ciudad | Atiende | Demanda relativa |
|---|---|---|---|---|
| CAL-01 | Bodega principal Acopi | Yumbo | Valle del Cauca, Cauca, Eje Cafetero, Antioquia | 1.0 |
| BOG-01 | Bodega satélite Bogotá | Bogotá | Bogotá, Cundinamarca, Boyacá | 0.4 |

### 2.3 Usuarios

Contraseña de todos los usuarios demo: `Xperp2026*`.

| Correo | Nombre | Rol | Bodega |
|---|---|---|---|
| admin@laceiba.demo | Administrador Demo | admin | |
| gerencia@laceiba.demo | Laura Méndez | manager | |
| compras@laceiba.demo | Andrés Rojas | purchasing | |
| bodega.cali@laceiba.demo | Jhon Caicedo | warehouse | CAL-01 |
| bodega.bogota@laceiba.demo | Diana Pardo | warehouse | BOG-01 |
| analista@laceiba.demo | Camila Torres | viewer | |

### 2.4 Categorías

| Código | Categoría |
|---|---|
| ABA | Abarrotes y granos |
| BEB | Lácteos y bebidas |
| ASE | Aseo del hogar |
| CPE | Cuidado personal |
| SNK | Snacks y confitería |

### 2.5 Catálogo

Costo y precio en COP por unidad de venta. La demanda base es en unidades por día en CAL-01; en BOG-01 se multiplica por 0.4. Las compras se redondean a cajas completas.

| SKU | Producto | Und/caja | Peso kg | Costo | Precio | Demanda base/día |
|---|---|---|---|---|---|---|
| ABA-001 | Arroz blanco 1 kg | 25 | 1.00 | 3.600 | 4.500 | 180 |
| ABA-002 | Arroz blanco 5 kg | 5 | 5.00 | 17.000 | 21.000 | 35 |
| ABA-003 | Fríjol cargamanto 500 g | 24 | 0.50 | 5.800 | 7.300 | 40 |
| ABA-004 | Lenteja 500 g | 24 | 0.50 | 3.200 | 4.100 | 55 |
| ABA-005 | Azúcar blanca 1 kg | 25 | 1.00 | 3.900 | 4.900 | 150 |
| ABA-006 | Sal refinada 1 kg | 25 | 1.00 | 1.300 | 1.800 | 60 |
| ABA-007 | Aceite vegetal 1 L | 12 | 0.92 | 7.800 | 9.800 | 120 |
| ABA-008 | Aceite vegetal 3 L | 6 | 2.75 | 21.500 | 26.500 | 30 |
| ABA-009 | Pasta spaghetti 500 g | 24 | 0.50 | 2.300 | 3.000 | 90 |
| ABA-010 | Harina de trigo 1 kg | 20 | 1.00 | 3.000 | 3.900 | 45 |
| ABA-011 | Café molido 500 g | 24 | 0.50 | 14.500 | 18.200 | 50 |
| ABA-012 | Panela 1 kg | 20 | 1.00 | 3.400 | 4.400 | 70 |
| ABA-013 | Atún en aceite 160 g | 48 | 0.17 | 5.200 | 6.800 | 75 |
| ABA-014 | Chocolate de mesa 500 g | 24 | 0.50 | 9.200 | 11.800 | 25 |
| BEB-001 | Leche entera UHT 1 L | 12 | 1.05 | 3.500 | 4.400 | 200 |
| BEB-002 | Leche en polvo 400 g | 24 | 0.42 | 13.800 | 17.200 | 30 |
| BEB-003 | Agua sin gas 600 ml | 24 | 0.62 | 900 | 1.500 | 160 |
| BEB-004 | Gaseosa sabor cola 1,5 L | 12 | 1.58 | 3.600 | 4.800 | 110 |
| BEB-005 | Jugo de naranja 1 L | 12 | 1.05 | 3.900 | 5.200 | 50 |
| BEB-006 | Bebida hidratante 500 ml | 12 | 0.53 | 2.300 | 3.300 | 65 |
| ASE-001 | Detergente en polvo 1 kg | 18 | 1.00 | 8.400 | 10.900 | 70 |
| ASE-002 | Jabón en barra para ropa 300 g | 40 | 0.30 | 2.200 | 2.900 | 85 |
| ASE-003 | Lavaloza en crema 500 g | 24 | 0.50 | 3.900 | 5.200 | 60 |
| ASE-004 | Blanqueador 1 L | 12 | 1.05 | 2.700 | 3.700 | 55 |
| ASE-005 | Suavizante 1 L | 12 | 1.02 | 6.300 | 8.400 | 30 |
| ASE-006 | Limpiador multiusos 1 L | 12 | 1.02 | 4.300 | 5.900 | 35 |
| ASE-007 | Papel higiénico x 12 rollos | 4 | 1.30 | 17.500 | 22.500 | 60 |
| ASE-008 | Toallas de cocina x 3 rollos | 8 | 0.45 | 6.800 | 8.900 | 25 |
| ASE-009 | Bolsas de basura x 10 | 24 | 0.25 | 3.100 | 4.300 | 40 |
| CPE-001 | Jabón de tocador 3 x 110 g | 24 | 0.35 | 6.200 | 8.200 | 45 |
| CPE-002 | Champú 400 ml | 12 | 0.43 | 11.200 | 14.900 | 30 |
| CPE-003 | Crema dental 75 ml | 48 | 0.12 | 3.800 | 5.200 | 70 |
| CPE-004 | Desodorante en barra 50 g | 24 | 0.08 | 8.900 | 11.900 | 25 |
| CPE-005 | Toallas higiénicas x 10 | 24 | 0.12 | 4.300 | 5.900 | 40 |
| CPE-006 | Pañales etapa 3 x 30 | 4 | 1.20 | 32.000 | 41.500 | 15 |
| CPE-007 | Cepillo dental | 48 | 0.03 | 2.800 | 3.900 | 20 |
| SNK-001 | Papas fritas 110 g | 24 | 0.11 | 3.600 | 4.900 | 80 |
| SNK-002 | Galletas de soda x 6 paquetes | 12 | 0.30 | 4.400 | 5.900 | 55 |
| SNK-003 | Galletas rellenas x 12 | 12 | 0.40 | 6.700 | 8.900 | 35 |
| SNK-004 | Chocolatina 30 g | 50 | 0.03 | 1.500 | 2.200 | 110 |
| SNK-005 | Maní salado 200 g | 24 | 0.20 | 4.800 | 6.500 | 20 |
| SNK-006 | Caramelos surtidos x 100 | 12 | 0.50 | 6.900 | 9.500 | 10 |

Perecederos: BEB-001 y BEB-005, con vida útil de 90 días. En el MVP no se manejan lotes ni vencimientos; solo se usa la marca de perecedero para reducir la cobertura objetivo.

### 2.6 Proveedores

Los datos de esta tabla viven en el ERP. El comportamiento simulado (rechazos, retrasos, entregas parciales) vive solo en `partners-mock`, porque el ERP no puede saber de antemano si un proveedor va a fallar.

| Código | Proveedor | Ciudad | Categorías | Factor de precio | Entrega a CAL-01 (días) | Pedido mínimo | Plazo de pago | Cumplimiento histórico |
|---|---|---|---|---|---|---|---|---|
| PRV-01 | Granos del Valle S.A.S. | Cali | ABA | 1.00 | 2 | 800.000 | 30 días | 95 % |
| PRV-02 | Molinera Andina S.A.S. | Bogotá | ABA | 0.95 | 4 | 1.500.000 | 45 días | 85 % |
| PRV-03 | Lácteos La Pradera S.A.S. | Tuluá | BEB-001, BEB-002 | 1.00 | 1 | 500.000 | 15 días | 92 % |
| PRV-04 | Bebidas del Trópico S.A.S. | Yumbo | BEB | 0.98 | 2 | 600.000 | 30 días | 93 % |
| PRV-05 | Químicos Hogar Limpio S.A.S. | Cali | ASE excepto ASE-007 | 1.00 | 3 | 700.000 | 30 días | 90 % |
| PRV-06 | Higiene Total Distribuciones S.A.S. | Medellín | ASE, CPE | 0.92 | 5 | 1.200.000 | 60 días | 70 % |
| PRV-07 | Cuidado Personal Andes S.A.S. | Bogotá | CPE | 1.00 | 3 | 800.000 | 30 días | 93 % |
| PRV-08 | Dulces y Pasabocas del Pacífico S.A.S. | Cali | SNK | 1.00 | 2 | 500.000 | 30 días | 90 % |
| PRV-09 | Mayorista Central S.A.S. | Cali | Todas | 1.10 | 1 | 0 | Contado | 98 % |

Reglas para generar las ofertas (`supplier_products`):

- `unit_cost = costo_del_producto × factor_de_precio`, redondeado a la decena.
- Preferidos: PRV-01 en ABA, PRV-03 en BEB-001 y BEB-002, PRV-04 en el resto de BEB, PRV-05 en ASE, PRV-06 en ASE-007, PRV-07 en CPE y PRV-08 en SNK.
- Alternos: PRV-02 en ABA, PRV-03 y PRV-04 entre sí en BEB, PRV-06 en ASE y CPE.
- PRV-09 vende todo y funciona como respaldo de emergencia: es más caro pero entrega al día siguiente.
- ASE-007 solo lo venden PRV-06 y PRV-09.
- Pedido mínimo por producto: 5 cajas.
- Entrega a BOG-01: proveedores de Bogotá, 1 día; los demás, el tiempo a CAL-01 más 2 días.

Comportamiento en `partners-mock/src/main/resources/partners.yml`:

| Proveedor | Prob. rechazo | Prob. retraso | Retraso (días) | Prob. entrega parcial |
|---|---|---|---|---|
| PRV-01 | 0.02 | 0.05 | 1–2 | 0.03 |
| PRV-02 | 0.08 | 0.15 | 1–3 | 0.05 |
| PRV-03 | 0.03 | 0.08 | 1 | 0.03 |
| PRV-04 | 0.02 | 0.07 | 1–2 | 0.02 |
| PRV-05 | 0.03 | 0.10 | 1–2 | 0.04 |
| PRV-06 | 0.05 | 0.30 | 2–5 | 0.10 |
| PRV-07 | 0.02 | 0.07 | 1–2 | 0.03 |
| PRV-08 | 0.03 | 0.10 | 1–2 | 0.04 |
| PRV-09 | 0.01 | 0.02 | 1 | 0.01 |

### 2.7 Transportadoras

| Código | Transportadora | Cobertura | Tarifa | Entrega | Cumplimiento |
|---|---|---|---|---|---|
| TRP-01 | Rápido Valle Logística | Cali, Yumbo, Jamundí, Palmira, Candelaria, Buga, Tuluá, Buenaventura | 18.000 + 450/kg | 1 día | 95 % |
| TRP-02 | Envíos Cordillera S.A.S. | Nacional | 25.000 + 600/kg | 2–4 días | 88 % |
| TRP-03 | TransCarga Express S.A.S. | Bogotá, Medellín, Cali, Pereira, Manizales, Armenia, Popayán, Tunja | 32.000 + 520/kg | 1–2 días | 96 % |

Los traslados entre bodegas (CAL-01 ↔ BOG-01) van con TRP-02 y tardan 2 días.

### 2.8 Clientes

Se generan 120 clientes con semilla fija (`SIM_RANDOM_SEED`).

| Segmento | Participación | Descuento | Líneas por pedido | Cantidad por línea |
|---|---|---|---|---|
| Tienda de barrio | 50 % | 0 % | 4–10 | 2–12 und |
| Minimercado | 25 % | 3 % | 8–15 | 6–36 und |
| Supermercado regional | 10 % | 7 % | 15–30 | 24–120 und |
| Mayorista | 5 % | 10 % | 10–25 | 1–5 cajas |
| Tienda en línea | 10 % | 0 % | 1–6 | 1–4 und |

Ciudades de CAL-01: Cali 55 %, Palmira 8 %, Jamundí 6 %, Yumbo 5 %, Tuluá 5 %, Buga 4 %, Buenaventura 4 %, Popayán 4 %, Pereira 4 %, Armenia 3 %, Medellín 2 %.
Ciudades de BOG-01: Bogotá 75 %, Soacha 10 %, Chía 8 %, Zipaquirá 4 %, Tunja 3 %.

Nombres: "Tienda Doña <nombre>", "Minimercado <apellido>", "Supermercados <apellido> S.A.S.", "Comercializadora <apellido>", "<nombre> en línea".

### 2.9 Inventario inicial

- Regla general: `on_hand = demanda_base × 15 días`, redondeado hacia arriba a cajas completas, en ambas bodegas (en BOG-01 con la demanda × 0.4).
- `reserved = 0`.
- `safety_stock`, `reorder_point` y `max_stock` los calcula el cargador a partir del historial de 90 días (sección 8.1).
- Excepciones, que existen para que pasen cosas desde el primer día:

| SKU | Bodega | Cobertura inicial | Para qué |
|---|---|---|---|
| ABA-007 | CAL-01 | 3 días | Dispara compra el día 1 |
| BEB-001 | CAL-01 | 2 días | Compra urgente con el proveedor más rápido |
| ASE-007 | CAL-01 | 4 días | Compra a PRV-06, que se retrasa |
| CPE-006 | BOG-01 | 0 días | Quiebre de stock; la IA propone traslado |
| CPE-006 | CAL-01 | 40 días | Origen del traslado |
| SNK-006 | CAL-01 | 90 días | Producto estancado |
| ASE-001 | CAL-01 | 20 días | Diferencia en el conteo físico del día 15 |

### 2.10 Patrón de demanda

La demanda diaria por SKU y bodega se calcula así:

```
demanda = base × factor_bodega × día_semana × quincena × festivo × temporada × ruido
```

| Factor | Valor |
|---|---|
| Día de la semana | Lun 1.05 · Mar 0.95 · Mié 0.95 · Jue 1.00 · Vie 1.15 · Sáb 1.20 · Dom 0.50 |
| Quincena | Días 15, 16, último del mes y 1 del mes siguiente: 1.25 |
| Festivo | El festivo: 0.40 · El día anterior: 1.15 |
| Temporada | Diciembre: BEB y SNK 1.50, el resto 1.20 |
| Ruido | Lognormal con σ = 0.15 |

Pedidos por día: unos 90 en CAL-01 y 40 en BOG-01, repartidos entre 7:00 y 19:00 con picos de 9:00 a 11:00 y de 15:00 a 17:00.

Si la demanda supera lo disponible, la línea se despacha parcial y lo que falta queda en `lost_sales`. Ese dato alimenta el KPI de nivel de servicio.

### 2.11 Festivos (tabla `holidays`)

2026-08-07, 2026-08-17, 2026-10-12, 2026-11-02, 2026-11-16, 2026-12-08, 2026-12-25, 2027-01-01, 2027-01-11.

### 2.12 Presupuesto mensual de compras

| Categoría | Presupuesto (COP) |
|---|---|
| ABA | 260.000.000 |
| BEB | 90.000.000 |
| ASE | 130.000.000 |
| CPE | 70.000.000 (ajustado a propósito: obliga a priorizar a final de mes) |
| SNK | 50.000.000 |

Tope diario de compras que puede ejecutar la IA: 40.000.000.

### 2.13 Guion de escenarios

Vive en `backend/src/main/resources/demo/scenarios.yml` y lo ejecuta el módulo `simulation`. Cada escenario es un evento con día y hora simulados.

| Día | Evento | Qué debe hacer Xperp |
|---|---|---|
| 1 | Aceite 1 L y leche UHT en CAL-01 arrancan bajos | El ciclo diario genera OC; la leche va a PRV-03 por tiempo de entrega |
| 1 | Pañales en 0 en BOG-01 y con 40 días en CAL-01 | Propone traslado en vez de comprar; pide aprobación |
| 2 | Festivo | Baja la demanda; no hay compras de pánico |
| 3 | Papel higiénico en CAL-01 con 4 días | OC a PRV-06 |
| 5 | PRV-06 reporta retraso de 4 días | Reasigna a PRV-09 y explica el sobrecosto |
| 6 | PRV-02 rechaza una OC de arroz por falta de stock | Reasigna a PRV-01 |
| 10 | PRV-01 sube el café 18 % | Alerta de precio; vuelve a calificar proveedores para ese SKU |
| 15 | Quincena | Pico de demanda; el pronóstico lo absorbe |
| 15 | Conteo de detergente en CAL-01: kardex 1.260, físico 1.170 | Alerta de discrepancia, ajuste pendiente de aprobación y tarea de reconteo |
| 16 | Festivo | |
| 20 | Un mayorista pide 10 veces su volumen normal de azúcar | Anomalía de venta: retiene el pedido para revisión antes de tratarlo como demanda real |
| 22 en adelante | CPE pasa el 85 % del presupuesto | Prioriza por margen y días de cobertura; las OC restantes quedan para aprobación |
| Todo el mes | Caramelos con 90 días de inventario | Sugiere promoción y bloquea la recompra |
| 30 | Cierre de mes | Resumen: compras, quiebres evitados, decisiones ejecutadas, aprobadas y rechazadas |

### 2.14 Dónde viven los datos y cómo se cargan

```
backend/src/main/resources/demo/
├── company.yml            # empresa, bodegas, usuarios, presupuestos, políticas por defecto
├── categories.csv
├── products.csv           # tabla 2.5
├── suppliers.csv          # tabla 2.6
├── supplier_offers.yml    # reglas de preferidos, alternos y excepciones
├── carriers.csv           # tabla 2.7
├── customer_segments.csv  # tabla 2.8
├── holidays.csv
├── initial_stock.yml      # regla general y excepciones
├── demand_profile.yml     # factores de la tabla 2.10
└── scenarios.yml          # guion de la tabla 2.13
```

`DemoDataLoader` corre con el perfil `demo` cuando la base está vacía:

1. Carga empresa, usuarios, catálogo, proveedores, ofertas, transportadoras, clientes y presupuestos usando los servicios públicos de cada módulo.
2. `HistoryGenerator` crea 90 días de ventas con el mismo patrón de demanda. Para que sea rápido usa inserción por lotes con `JdbcTemplate`; es la única excepción a la regla de pasar por servicios.
3. Calcula los parámetros de inventario a partir del historial.
4. Fija el inventario inicial.
5. Deja el reloj en `SIM_START_DATE 00:00`, en pausa.

Con la misma semilla, la carga da siempre el mismo resultado. `POST /api/v1/simulation/reset` borra todo y vuelve a cargar.

---

## 3. Stack

### 3.1 Elegido

| Capa | Tecnología |
|---|---|
| Backend | Java 21 LTS, Spring Boot 3.5.x, Maven (wrapper incluido) |
| Módulos | Spring Modulith: límites entre módulos verificados en pruebas y eventos persistidos |
| IA | Spring AI: `ChatClient`, tool calling con `@Tool`, proveedor configurable (Anthropic, OpenAI u Ollama) |
| Persistencia | Spring Data JPA (Hibernate 6), PostgreSQL 16, Flyway |
| Multiempresa | Columna `company_id` con `@TenantId` de Hibernate; la empresa se toma del JWT |
| Seguridad | Spring Security, JWT firmado con HS256, roles |
| Contratos | OpenAPI 3.1 escrito a mano; `openapi-generator-maven-plugin` genera las interfaces de los controllers |
| Tiempo real | Server-Sent Events (SSE) |
| Tareas programadas | `@Scheduled` atado al reloj simulado |
| Estadística | Apache Commons Math 3; Holt-Winters implementado en el proyecto |
| Datos falsos | Datafaker con semilla fija |
| PDF de guías y OC | OpenPDF |
| Correo | Spring Mail hacia Mailpit (bandeja local para ver correos a proveedores y bodega) |
| Pruebas backend | JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL), WireMock |
| Formato Java | Spotless con google-java-format |
| Frontend | SvelteKit 2, Svelte 5 (runes), TypeScript estricto, Vite |
| Modo del frontend | SPA con `adapter-static` y `ssr = false` |
| Estilos | CSS nativo con custom properties (tokens) y estilos con alcance por componente. Sin Tailwind |
| Cliente API | `openapi-typescript` (tipos generados) + `openapi-fetch` |
| Gráficas | Apache ECharts envuelto en un componente Svelte |
| Iconos | Lucide para Svelte |
| Validación de formularios | Zod |
| Mocks en frontend | MSW (Mock Service Worker) |
| Pruebas frontend | Vitest, Testing Library, `svelte-check`; Playwright para 2 o 3 flujos clave |
| Calidad frontend | ESLint, Prettier |
| Infraestructura | Docker Compose; GitHub Actions para CI |

### 3.2 Por qué este stack

- Spring AI resuelve el tool calling con anotaciones y permite cambiar de proveedor de LLM sin tocar el código del agente.
- Con Spring Modulith, cada integrante es dueño de sus paquetes y un test falla si alguien usa clases internas de otro módulo.
- Con el contrato OpenAPI primero, el backend no compila si se aparta del contrato y el frontend trabaja con MSW antes de que exista el endpoint.
- SSE es más simple que WebSocket para lo que se necesita: el servidor empuja eventos y el cliente solo escucha.
- Como el pronóstico es Holt-Winters con estacionalidad semanal, se puede implementar en Java sin montar un servicio aparte en Python.

---

## 4. Arquitectura

Monolito modular más un servicio aparte que simula a los socios externos.

```
frontend (SvelteKit SPA)
   │  REST /api/v1  +  SSE /api/v1/stream
   ▼
backend (Spring Boot)
   ├── módulos de dominio: catalog, inventory, sales, purchasing, logistics, finance
   ├── ai: reglas → pronóstico → puntaje → agente (Spring AI) → políticas → acción
   ├── simulation: reloj, generador de ventas, bodeguero virtual, guion
   ├── notifications: en la app, correo, SSE
   └── partners: clientes HTTP y webhooks
   │                      ▲
   │ HTTP                 │ webhooks firmados (HMAC)
   ▼                      │
partners-mock (Spring Boot): proveedores PRV-01..09, transportadoras TRP-01..03
   
PostgreSQL · Mailpit (SMTP local) · Ollama (opcional)
```

### 4.1 Flujo de venta y despacho

1. El simulador (o un usuario) crea un pedido de venta.
2. `sales` reserva stock en `inventory`: `reserved` sube y `on_hand` no cambia.
3. La IA crea las tareas de alistamiento y empaque y se las asigna al bodeguero de esa bodega (nivel 3).
4. El bodeguero, real o virtual, inicia y termina cada tarea.
5. Con el pedido empacado, la IA cotiza con las transportadoras que cubren la ciudad, elige una y genera la guía (nivel 2).
6. Al despachar se registra la salida en el kardex: bajan `on_hand` y `reserved`.
7. La transportadora actualiza el estado por webhook hasta la entrega.

### 4.2 Flujo de reabastecimiento

1. El ciclo diario de las 06:00 simuladas revisa todos los SKU de cada bodega. Además, si un SKU queda por debajo del stock de seguridad durante el día, se dispara una revisión urgente de ese SKU.
2. Reglas: si `disponible + en_tránsito ≤ punto_de_reorden`, se calcula la cantidad sugerida.
3. Puntaje: se califica cada oferta de proveedor.
4. Si una oferta gana con claridad, se decide por reglas y la justificación sale de plantilla. Si hay empate técnico, presupuesto insuficiente o una excepción, decide el agente LLM.
5. Se consolidan los SKU que van al mismo proveedor en una sola OC, respetando el pedido mínimo.
6. La capa de políticas decide si se ejecuta, va a aprobación o queda como sugerencia.
7. Al ejecutarse, la OC se envía a `partners-mock` y se manda por correo al proveedor con el PDF adjunto.
8. El proveedor responde por webhook: `confirmed`, `rejected`, `delayed`, `shipped` o `delivered`.
9. Ante un rechazo o un retraso, la IA evalúa reasignar, esperar o cancelar.
10. Con `delivered` se crea una tarea de recepción. Al completarla se registra la recepción y sube el stock.
11. Cada paso queda en `ai_decisions` y sale por SSE al tablero.

### 4.3 Flujo de traslado entre bodegas

1. Hay quiebre o riesgo en una bodega y la otra tiene más de 30 días de cobertura de ese SKU.
2. La IA propone el traslado (nivel 1, lo aprueba `manager`).
3. Se crean las tareas `transfer_out` en origen y `transfer_in` en destino; el traslado tarda 2 días con TRP-02.

---

## 5. Estructura del repositorio

```
xperp/
├── AGENTS.md                      # este archivo (para Claude Code se puede copiar como CLAUDE.md)
├── README.md                      # cómo levantar el proyecto
├── docker-compose.yml
├── .env.example
├── .gitignore
├── .editorconfig
├── .github/
│   ├── workflows/
│   │   ├── backend.yml
│   │   ├── frontend.yml
│   │   └── contracts.yml
│   ├── CODEOWNERS
│   └── pull_request_template.md
├── contracts/
│   ├── openapi/
│   │   ├── openapi.yaml           # raíz: info, seguridad, $ref a cada módulo
│   │   ├── paths/                 # un archivo por módulo: inventory.yaml, purchasing.yaml, ...
│   │   └── schemas/               # un archivo por módulo
│   ├── events.md                  # eventos de dominio y eventos SSE
│   └── partners.yaml              # API de proveedores y transportadoras simulados
├── docs/
│   ├── architecture.md
│   ├── erd.md                     # modelo entidad-relación en Mermaid
│   ├── demo-script.md             # guion de la presentación minuto a minuto
│   └── decisions/                 # ADR: 0001-stack.md, 0002-autonomia.md, ...
├── backend/
├── partners-mock/
└── frontend/
```

Dividir el contrato OpenAPI por módulo evita que los dos editen el mismo archivo al mismo tiempo.

### 5.1 Backend

```
backend/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/xperp/
    │   │   ├── XperpApplication.java
    │   │   ├── shared/              # módulo abierto: config, seguridad, tenancy, reloj, errores, entidad base, SSE
    │   │   ├── catalog/
    │   │   ├── inventory/
    │   │   ├── sales/
    │   │   ├── purchasing/
    │   │   ├── logistics/
    │   │   ├── finance/
    │   │   ├── partners/            # clientes HTTP a partners-mock y controllers de webhooks
    │   │   ├── notifications/
    │   │   ├── dashboard/           # KPIs, solo lectura
    │   │   ├── ai/
    │   │   │   ├── api/             # AiFacade: lo único que ven otros módulos
    │   │   │   ├── rules/           # stock de seguridad, punto de reorden, cantidad a pedir
    │   │   │   ├── forecasting/     # Holt-Winters y media móvil
    │   │   │   ├── scoring/         # puntaje de proveedores y transportadoras
    │   │   │   ├── anomalies/
    │   │   │   ├── policies/        # PolicyEngine, ActionGateway
    │   │   │   ├── agent/           # AgentOrchestrator, LlmGateway (Spring AI), MockLlmGateway
    │   │   │   ├── tools/           # clases con métodos @Tool
    │   │   │   ├── decisions/       # bitácora, aprobaciones, reversión
    │   │   │   └── assistant/       # chat del tablero, solo lectura
    │   │   └── simulation/
    │   │       ├── clock/           # SimulationClock y ticker
    │   │       ├── demand/          # perfil de demanda y generador de pedidos
    │   │       ├── actors/          # bodeguero virtual
    │   │       ├── scenarios/       # ejecución de scenarios.yml
    │   │       └── seed/            # DemoDataLoader, HistoryGenerator
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-demo.yml
    │       ├── db/migration/        # V1__platform.sql, V2__catalog.sql, V3__inventory.sql, ...
    │       ├── demo/                # datos de la sección 2.14
    │       └── prompts/             # replenishment.v1.st, po-exception.v1.st, anomaly.v1.st, daily-summary.v1.st
    └── test/java/com/xperp/
        ├── ModularityTest.java      # ApplicationModules.of(XperpApplication.class).verify()
        └── <modulo>/...
```

Estructura interna de cada módulo de dominio:

```
inventory/
├── api/          # interfaces públicas (InventoryService) y DTO como records. Lo único accesible desde otros módulos
├── domain/       # entidades JPA y enums
├── repository/   # Spring Data JPA
├── service/      # implementación, package-private cuando se pueda
├── web/          # controllers que implementan las interfaces generadas desde el contrato
└── events/       # records de eventos que publica el módulo
```

Dependencias permitidas: `web → service → repository`. Otro módulo solo usa `api` y `events`. El código generado desde OpenAPI queda en `target/generated-sources` y no se sube al repo.

### 5.2 partners-mock

```
partners-mock/
├── pom.xml
├── Dockerfile
└── src/main/
    ├── java/com/xperp/partners/
    │   ├── suppliers/     # recibe OC y programa respuestas según partners.yml
    │   ├── carriers/      # cotiza, crea envíos y programa cambios de estado
    │   ├── clock/         # lee el reloj simulado del backend cada segundo
    │   ├── scenarios/     # eventos forzados del guion (rechazo del día 6, retraso del día 5, alza del día 10)
    │   └── webhook/       # cliente con firma HMAC
    └── resources/
        ├── application.yml
        └── partners.yml
```

### 5.3 Frontend

```
frontend/
├── package.json
├── svelte.config.js              # adapter-static con fallback a index.html
├── vite.config.ts
├── tsconfig.json                 # strict: true
├── static/
└── src/
    ├── app.html
    ├── app.d.ts
    ├── lib/
    │   ├── api/
    │   │   ├── schema.d.ts       # generado desde contracts/openapi; no se edita a mano
    │   │   ├── client.ts         # openapi-fetch con token y manejo de errores
    │   │   └── stream.ts         # conexión SSE con reconexión
    │   ├── stores/               # auth.svelte.ts, clock.svelte.ts, notifications.svelte.ts
    │   ├── components/
    │   │   ├── ui/               # Button, Input, Select, DataTable, Modal, Drawer, Badge, Toast, Tabs, EmptyState
    │   │   ├── charts/           # EChart.svelte y gráficas específicas
    │   │   └── domain/           # KpiTile, StockBadge, DecisionCard, ApprovalCard, TaskCard, Timeline
    │   ├── styles/
    │   │   ├── tokens.css        # colores, tipografía, espaciado, radios, sombras; tema claro y oscuro
    │   │   ├── base.css
    │   │   └── utilities.css
    │   └── utils/                # format.ts (COP, fechas en America/Bogota), permissions.ts
    ├── mocks/                    # MSW: un archivo de handlers por módulo + browser.ts
    └── routes/
        ├── +layout.ts            # export const ssr = false
        ├── login/
        └── (app)/
            ├── +layout.svelte    # menú lateral, barra superior con reloj simulado y notificaciones, guard de sesión
            ├── tablero/
            ├── inventario/       # lista con semáforo, detalle y kardex
            ├── ventas/
            ├── compras/          # OC, proveedores, ofertas
            ├── bodega/           # tareas del usuario; pensada para celular
            ├── despachos/        # envíos y guías
            ├── ia/
            │   ├── decisiones/   # bitácora con razonamiento y alternativas
            │   ├── aprobaciones/
            │   └── politicas/    # niveles, umbrales, kill switch, modo sombra
            ├── asistente/
            ├── simulacion/       # iniciar, pausar, velocidad, reiniciar, ver guion
            └── configuracion/
```

Las rutas de la interfaz van en español porque las ve el usuario. El código va en inglés.

### 5.4 Puertos

| Servicio | Puerto |
|---|---|
| frontend (dev) | 5173 |
| frontend (Docker, nginx) | 3000 |
| backend | 8080 |
| partners-mock | 8101 |
| PostgreSQL | 5432 |
| Mailpit (interfaz web) | 8025 |
| Mailpit (SMTP) | 1025 |
| Ollama (opcional) | 11434 |

---

## 6. Modelo de datos

Convenciones:

- Tablas en inglés, `snake_case`, en plural.
- PK `id bigint generated always as identity`.
- Todas las tablas de negocio llevan `company_id`, salvo `companies`, `holidays` y las de sistema.
- Todas llevan `created_at` y `updated_at` (`timestamptz`).
- Montos en `numeric(14,2)` y `BigDecimal` en Java. Nunca `double`.
- Cantidades en `integer`, en unidades de venta.
- Borrado lógico con `is_active`. No se borra nada que tenga movimientos.
- Documentos numerados por empresa: `PV-2026-000001` (pedido de venta), `OC-2026-000001`, `TR-2026-000001` (traslado).

### 6.1 Plataforma
- `companies`: id, name, nit, city, timezone, currency, is_active
- `users`: id, company_id, email, password_hash, full_name, role, warehouse_id, is_active
- `holidays`: date, name
- `simulation_state`: company_id, sim_time, status (`paused`, `running`), seconds_per_day, scenario_cursor
- `event_publication`: la crea y la usa Spring Modulith

### 6.2 Catálogo
- `categories`: id, code, name
- `products`: id, sku (único por empresa), name, category_id, unit, units_per_case, weight_kg, cost, price, perishable, shelf_life_days, is_active

### 6.3 Inventario
- `warehouses`: id, code, name, city, address
- `inventory`: product_id + warehouse_id (PK), on_hand, reserved, safety_stock, reorder_point, cover_days, max_stock, params_updated_at
- `inventory_movements`: id, product_id, warehouse_id, type (`sale`, `purchase_receipt`, `adjustment`, `transfer_out`, `transfer_in`, `return`), quantity (con signo), balance_after, unit_cost, reference_type, reference_id, occurred_at, created_by
- `inventory_counts`: id, product_id, warehouse_id, system_qty, counted_qty, status, counted_by, counted_at
- `stock_transfers`: id, number, from_warehouse_id, to_warehouse_id, status, ai_decision_id, shipped_at, received_at
- `stock_transfer_items`: id, stock_transfer_id, product_id, quantity
- `lost_sales`: id, product_id, warehouse_id, sales_order_id, quantity, occurred_at

Disponible = `on_hand - reserved`. En tránsito se calcula con las OC y traslados abiertos; no se guarda en una columna.

### 6.4 Ventas
- `customer_segments`: code, name, discount_pct
- `customers`: id, code, name, document, segment_code, city, address, warehouse_id, is_active
- `sales_orders`: id, number, customer_id, warehouse_id, status (`created`, `on_hold`, `picking`, `packed`, `shipped`, `delivered`, `cancelled`), subtotal, discount, total, hold_reason, ordered_at, promised_at
- `sales_order_items`: id, sales_order_id, product_id, quantity, quantity_fulfilled, unit_price

### 6.5 Compras
- `suppliers`: id, code, name, nit, city, email, partner_code, min_order_amount, payment_terms_days, on_time_rate, fill_rate, is_active
- `supplier_warehouses`: supplier_id, warehouse_id, lead_time_days
- `supplier_products`: supplier_id, product_id, unit_cost, min_order_cases, is_preferred, price_updated_at
- `supplier_price_history`: id, supplier_id, product_id, unit_cost, valid_from
- `purchase_orders`: id, number, supplier_id, warehouse_id, status (`draft`, `pending_approval`, `sent`, `confirmed`, `delayed`, `rejected`, `shipped`, `received`, `cancelled`), subtotal, total, expected_at, sent_at, confirmed_at, received_at, created_by_user_id (nulo si la creó la IA), ai_decision_id, replaces_po_id, idempotency_key (único), external_id
- `purchase_order_items`: id, purchase_order_id, product_id, quantity, unit_cost
- `goods_receipts`: id, purchase_order_id, received_by, received_at
- `goods_receipt_items`: id, goods_receipt_id, product_id, quantity_received

### 6.6 Logística
- `carriers`: id, code, name, partner_code, coverage_cities (jsonb), on_time_rate, is_active
- `warehouse_tasks`: id, warehouse_id, type (`pick`, `pack`, `receive`, `count`, `transfer_out`, `transfer_in`), status (`pending`, `in_progress`, `done`, `cancelled`), priority, sales_order_id, purchase_order_id, stock_transfer_id, assigned_to_user_id, due_at, started_at, completed_at, completed_by
- `shipments`: id, sales_order_id, carrier_id, service, tracking_number, status, cost, weight_kg, eta, label_path, shipped_at, delivered_at

### 6.7 Finanzas
- `budgets`: id, category_id, period (`YYYY-MM`), amount, committed, spent
- `accounts_payable`: id, purchase_order_id, supplier_id, amount, due_date, status

`committed` sube cuando se envía la OC y `spent` cuando se recibe. La política valida que `committed + spent + nueva_oc ≤ amount`.

### 6.8 IA y control
- `demand_forecasts`: product_id, warehouse_id, forecast_date, quantity, model, generated_at
- `ai_decisions`: id, decision_type, trigger_event, trigger_ref, input_snapshot (jsonb), alternatives (jsonb), proposed_action (jsonb), reasoning, confidence, policy_result (`executed`, `pending_approval`, `suggested`, `blocked`), status, approved_by, executed_at, reverted_at, llm_used, llm_model, prompt_version, tokens_in, tokens_out, latency_ms, sim_time
- `autonomy_policies`: id, action_type, level, min_amount, max_amount, approver_role, conditions (jsonb), is_enabled
- `approvals`: id, ai_decision_id, requested_role, status, decided_by, decided_at, comment
- `alerts`: id, type, severity, message, entity_type, entity_id, is_read
- `notifications`: id, user_id, role, type, title, body, link, read_at
- `system_settings`: key, value (jsonb). Claves: `kill_switch`, `shadow_mode`, `ai_daily_spend_cap`, `llm_max_calls_per_day`, `scoring_weights`
- `assistant_messages`: id, user_id, conversation_id, role, content, tool_calls (jsonb)

---

## 7. Contratos

### 7.1 Reglas

- Un cambio de contrato va en su propio PR (`contract/<modulo>-<descripcion>`), lo aprueban los dos y se fusiona antes de implementarlo.
- El backend implementa las interfaces generadas; si el código se aparta del contrato, no compila.
- El frontend regenera `schema.d.ts` con `npm run gen:api` y crea los handlers de MSW para el endpoint nuevo.
- REST bajo `/api/v1`, recursos en plural y kebab-case, JSON en `snake_case`.
- Paginación: `?page=1&size=20` → `{ items, total, page, size }`.
- Errores: `{ code, message, details }` con el código HTTP correcto.
- Fechas ISO 8601 con zona horaria. Montos como string decimal (`"125000.00"`).

### 7.2 Endpoints

| Módulo | Endpoints |
|---|---|
| auth | `POST /auth/login`, `GET /auth/me` |
| catalog | `GET /categories`, `GET POST /products`, `GET PUT /products/{id}` |
| inventory | `GET /warehouses`, `GET /inventory?warehouse_id&status=low\|ok\|over`, `GET /inventory/{product_id}/{warehouse_id}/movements`, `POST /inventory/adjustments`, `POST /inventory/counts`, `GET POST /stock-transfers` |
| sales | `GET /customers`, `GET POST /sales-orders`, `GET /sales-orders/{id}`, `POST /sales-orders/{id}/cancel`, `POST /sales-orders/{id}/release` |
| purchasing | `GET /suppliers`, `GET /suppliers/{id}/offers`, `GET POST /purchase-orders`, `GET /purchase-orders/{id}`, `POST /purchase-orders/{id}/send`, `POST /purchase-orders/{id}/cancel`, `GET /purchase-orders/{id}/pdf` |
| logistics | `GET /warehouse-tasks?assigned_to=me&status`, `POST /warehouse-tasks/{id}/start`, `POST /warehouse-tasks/{id}/complete`, `GET /carriers`, `GET /shipments`, `GET /shipments/{id}/label` |
| finance | `GET /budgets?period`, `GET /accounts-payable` |
| ai | `GET /ai/decisions`, `GET /ai/decisions/{id}`, `POST /ai/decisions/{id}/revert`, `GET /approvals?status`, `POST /approvals/{id}/approve`, `POST /approvals/{id}/reject`, `GET PUT /ai/policies`, `GET PUT /ai/settings` |
| assistant | `POST /assistant/messages`, `GET /assistant/conversations/{id}` |
| dashboard | `GET /dashboard/kpis?from&to&warehouse_id`, `GET /dashboard/series?metric&from&to` |
| notifications | `GET /notifications`, `POST /notifications/{id}/read`, `GET /stream` (SSE) |
| simulation | `GET /simulation/clock`, `POST /simulation/start`, `POST /simulation/pause`, `PUT /simulation/speed`, `POST /simulation/reset` |
| webhooks | `POST /webhooks/suppliers`, `POST /webhooks/carriers` |

### 7.3 Webhooks

Encabezado `X-Xperp-Signature: sha256=<hmac del cuerpo con WEBHOOK_SECRET>`. Si la firma no coincide, el backend responde 401.

```json
// proveedores
{ "external_id": "PRV02-00045", "po_number": "OC-2026-000012", "status": "rejected",
  "expected_at": null, "reason": "Sin existencias", "items": [{ "sku": "ABA-001", "quantity": 1500 }] }

// transportadoras
{ "tracking_number": "TRP01-0000231", "status": "in_transit", "occurred_at": "2026-11-04T10:15:00-05:00" }
```

### 7.4 API de partners-mock (`contracts/partners.yaml`)

- `POST /suppliers/{code}/orders` → `{ external_id, status: "received" }`
- `GET /suppliers/{code}/orders/{external_id}`
- `GET /suppliers/{code}/catalog` (precios vigentes; aquí aparece el alza del día 10)
- `POST /carriers/{code}/quotes` con `{ origin_city, destination_city, weight_kg }` → `{ service, cost, eta_days }`
- `POST /carriers/{code}/shipments` → `{ tracking_number }`

Se autentica con `Authorization: Bearer <PARTNERS_API_TOKEN>`.

### 7.5 Eventos de dominio

Se publican con `ApplicationEventPublisher` y se consumen con `@ApplicationModuleListener` de Spring Modulith (asíncrono, después del commit y persistido en `event_publication`).

| Evento | Lo publica | Lo consumen |
|---|---|---|
| `SalesOrderCreated` | sales | inventory, ai |
| `StockReserved` / `StockReleased` | inventory | dashboard |
| `StockBelowSafety` | inventory | ai (revisión urgente) |
| `InventoryCountRecorded` | inventory | ai (discrepancias) |
| `PurchaseOrderSent` | purchasing | finance, notifications |
| `PurchaseOrderStatusChanged` | partners | purchasing, ai, notifications |
| `SupplierPriceChanged` | partners | purchasing, ai |
| `GoodsReceived` | purchasing | inventory, finance |
| `WarehouseTaskCompleted` | logistics | sales, ai |
| `SalesOrderPacked` | logistics | ai (transportadora) |
| `ShipmentStatusChanged` | partners | logistics, sales, notifications |
| `AiDecisionRecorded` | ai | notifications |
| `ApprovalRequested` / `ApprovalResolved` | ai | notifications, ai |
| `SimulationDayStarted` / `SimulationDayEnded` | simulation | ai, dashboard |

### 7.6 Eventos SSE

`GET /api/v1/stream` envía eventos con `event:` y JSON en `data:`. El frontend usa `fetch` con el token en el encabezado, no `EventSource`, que no permite encabezados.

`clock.tick` (cada hora simulada), `sale.created`, `inventory.changed`, `purchase_order.changed`, `task.created`, `task.changed`, `shipment.changed`, `ai.decision`, `approval.requested`, `alert.created`, `notification.created`.

---

## 8. Capa de IA

### 8.1 Reglas

- Demanda diaria: promedio y desviación de los últimos 28 días, o pronóstico si existe.
- Stock de seguridad: `z × σ_diaria × √lead_time`, con `z = 1.65` (nivel de servicio del 95 %).
- Punto de reorden: `demanda_diaria × lead_time + stock_de_seguridad`.
- Cobertura objetivo (`cover_days`): 10 días; 5 en perecederos.
- Cantidad a pedir: `pronóstico(lead_time + cover_days) + stock_de_seguridad − disponible − en_tránsito`, redondeada hacia arriba a cajas, con mínimo `min_order_cases` y tope en `max_stock` (30 días de demanda).
- Se recalculan una vez por día simulado (decisión `reorder_params_update`, nivel 2).

Todo vive en `ai/rules` como funciones puras con pruebas unitarias.

### 8.2 Pronóstico

- Holt-Winters aditivo con estacionalidad de 7 días, por SKU y bodega, a 30 días.
- Con menos de 28 días de historia se usa media móvil ponderada.
- Después del pronóstico base se aplican los factores conocidos: quincena, festivos y temporada.
- Se guarda en `demand_forecasts` cada día simulado a las 05:00.

### 8.3 Puntaje

Proveedores: `0.40 × precio + 0.25 × tiempo_de_entrega + 0.25 × cumplimiento + 0.10 × preferido`. Precio y tiempo se normalizan entre las ofertas del mismo SKU (menor es mejor). Los pesos están en `system_settings.scoring_weights`.

Transportadoras: `0.5 × costo + 0.3 × tiempo + 0.2 × cumplimiento`, entre las que cubren la ciudad de destino.

Hay empate técnico cuando la diferencia entre las dos primeras es menor a 0.05. Ahí decide el LLM.

### 8.4 Catálogo de decisiones

| Tipo | Disparador | Acción | Nivel por defecto |
|---|---|---|---|
| `replenishment` | Ciclo diario o `StockBelowSafety` | `create_purchase_order` | Según monto (9.2) |
| `po_exception` | OC rechazada o retrasada | `reassign_purchase_order`, `cancel_purchase_order` o esperar | 2 si el sobrecosto es ≤ 10 %; si no, 1 |
| `stock_transfer` | Quiebre en una bodega y más de 30 días de cobertura en la otra | `propose_stock_transfer` | 1 (manager) |
| `fulfillment` | `SalesOrderCreated` | `create_warehouse_task` | 3 |
| `carrier_selection` | `SalesOrderPacked` | `create_shipment` | 2 |
| `demand_anomaly` | Línea de pedido 5 veces mayor que lo habitual en ese cliente y SKU | `hold_sales_order` + `raise_alert` | 2 |
| `price_anomaly` | Cambio de costo mayor al 10 % | `raise_alert` y nueva calificación | 3 |
| `inventory_discrepancy` | Conteo con diferencia mayor al 3 % | `raise_alert`, `create_warehouse_task(count)`, ajuste propuesto | Ajuste en 1 |
| `slow_mover` | Cobertura mayor a 60 días | `suggest_promotion`, bloquear recompra | 0 |
| `budget_prioritization` | Presupuesto de la categoría por encima del 85 % | Reordenar OC pendientes por margen y cobertura | 1 |
| `reorder_params_update` | Inicio del día simulado | `update_reorder_params` | 2 |
| `daily_summary` | Fin del día simulado | Notificación a gerencia y compras | 3 |
| `assistant_answer` | Pregunta en el chat | Solo lectura | n/a |

### 8.5 Cuándo se usa el LLM

Para que la demo sea rápida y barata, el LLM no participa en todo:

- Sin LLM: reabastecimiento con ganador claro, tareas de bodega, transportadora con ganador claro, parámetros de reorden. La justificación sale de una plantilla con los números.
- Con LLM: empates técnicos, rechazos y retrasos, anomalías, priorización por presupuesto, traslados, resumen diario y asistente.
- Límite: `LLM_MAX_CALLS_PER_SIM_DAY` (30 por defecto). Si se pasa, se decide por reglas y queda marcado.
- `LLM_PROVIDER=mock` usa `MockLlmGateway`: elige siempre la opción de mayor puntaje y redacta con plantilla. La demo tiene que funcionar completa en este modo.

### 8.6 Tools

Las tools de lectura devuelven datos. Las de escritura no ejecutan nada por su cuenta: llaman a `ActionGateway.submit(...)` y le devuelven al LLM el resultado de la política ("ejecutada OC-2026-000031", "pendiente de aprobación #12", "bloqueada: kill switch").

| Tool | Tipo |
|---|---|
| `get_stock(sku, warehouse)` | lectura |
| `get_forecast(sku, warehouse, days)` | lectura |
| `list_supplier_offers(sku, warehouse)` | lectura |
| `get_open_purchase_orders(sku)` | lectura |
| `get_budget_status(category)` | lectura |
| `get_customer_history(customer_id, sku)` | lectura |
| `quote_carriers(sales_order_id)` | lectura |
| `query_kpis(metric, from, to, warehouse)` | lectura |
| `create_purchase_order(supplier, warehouse, items, reason)` | escritura |
| `reassign_purchase_order(po_number, new_supplier, reason)` | escritura |
| `cancel_purchase_order(po_number, reason)` | escritura |
| `propose_stock_transfer(sku, from, to, quantity, reason)` | escritura |
| `create_warehouse_task(type, reference, priority)` | escritura |
| `create_shipment(sales_order_id, carrier, service)` | escritura |
| `hold_sales_order(sales_order_id, reason)` | escritura |
| `raise_alert(type, severity, message)` | escritura |
| `suggest_promotion(sku, reason)` | escritura (solo sugerencia) |

### 8.7 Ejecución de una acción

1. Llega la propuesta (de reglas o del LLM).
2. Se valida la forma con Bean Validation.
3. El servicio del módulo valida el negocio: proveedor activo, pedido mínimo, stock suficiente, OC duplicada.
4. `PolicyEngine` revisa kill switch, modo sombra, nivel de autonomía, monto, presupuesto y tope diario.
5. Según el resultado: se ejecuta, se crea una aprobación o queda como sugerencia.
6. Siempre se escribe en `ai_decisions`, también si quedó bloqueada.
7. Se publica `AiDecisionRecorded`, que llega al tablero por SSE.

### 8.8 Prompts

- Viven en `resources/prompts/` en formato StringTemplate (`.st`), con versión en el nombre.
- Le piden al modelo responder en español, en máximo 3 frases de justificación, citando los números que usó.
- La versión del prompt se guarda en `ai_decisions.prompt_version`.

### 8.9 Asistente

Responde preguntas como "¿qué se agota esta semana?", "¿por qué se le compró a Mayorista Central?" o "¿cuánto llevamos gastado en aseo?". Solo usa tools de lectura y nunca genera SQL.

---

## 9. Controles

### 9.1 Niveles de autonomía

| Nivel | Comportamiento |
|---|---|
| 0 | Solo sugiere |
| 1 | Prepara la acción y espera aprobación |
| 2 | Ejecuta y notifica |
| 3 | Ejecuta sin notificar (bajo riesgo) |

### 9.2 Umbrales de órdenes de compra

| Monto de la OC | Comportamiento |
|---|---|
| Menos de 5.000.000 | Nivel 2: se ejecuta y se notifica |
| De 5.000.000 a 20.000.000 | Aprobación de `purchasing` |
| Más de 20.000.000 | Aprobación de `manager` |

Se editan desde **IA → Políticas** y se guardan en `autonomy_policies`.

### 9.3 Otros controles

- Presupuesto mensual por categoría y tope diario de compras de la IA.
- Kill switch: todas las acciones de escritura quedan `blocked`.
- Modo sombra: la IA decide pero nada se ejecuta; todo queda `suggested`. Sirve para medir cuánto acertaría frente a lo que hizo una persona.
- Idempotencia: `idempotency_key = sha256(company, sku, warehouse, trigger_ref)` con restricción única, y no se crea una OC si ya hay otra abierta para el mismo SKU y bodega.
- La IA no puede crear proveedores, cambiar precios pactados, borrar registros ni tocar usuarios.
- Toda decisión ejecutada se puede revertir mientras sea reversible (una OC no confirmada, una tarea pendiente, un pedido retenido).
- Las aprobaciones vencen a las 24 horas simuladas y quedan como `expired`.

### 9.4 Roles

| Rol | Puede |
|---|---|
| admin | Todo, incluidas políticas, kill switch y simulación |
| manager | Ver todo, aprobar OC grandes, traslados y ajustes de inventario |
| purchasing | Proveedores, OC, aprobar OC medianas |
| warehouse | Sus tareas, recepciones y conteos en su bodega |
| viewer | Solo lectura |

### 9.5 KPIs del tablero

Ventas del día y del mes, margen bruto, nivel de servicio (unidades despachadas / pedidas), ventas perdidas, SKU en quiebre, días de inventario por categoría, rotación, OC abiertas y atrasadas, cumplimiento por proveedor, ejecución del presupuesto, decisiones de la IA por estado, tiempo promedio de aprobación y sobrecosto evitado o asumido.

---

## 10. Simulación

### 10.1 Reloj

- `SimulationClock` extiende `java.time.Clock` y es un bean. Todo el código pide la hora con `Instant.now(clock)` o `LocalDate.now(clock)`.
- Un ticker de `@Scheduled(fixedRate = 1000)` avanza `86400 / SIM_SECONDS_PER_DAY` segundos simulados por cada segundo real. Con 20, un día dura 20 segundos y un mes unos 10 minutos.
- El estado se guarda en `simulation_state`, así que si se reinicia el backend la simulación sigue donde iba.
- Al cambiar de día se publica `SimulationDayStarted` (pronóstico a las 05:00, ciclo de reabastecimiento a las 06:00) y al cerrar, `SimulationDayEnded` (resumen).
- `partners-mock` lee el reloj con `GET /api/v1/simulation/clock` para programar sus respuestas en tiempo simulado.

### 10.2 Actores virtuales

- Generador de demanda: crea pedidos de venta a través de `SalesService`, como lo haría cualquier cliente.
- Bodeguero virtual: toma las tareas pendientes y las completa entre 1 y 3 horas simuladas después. Si un usuario real abre una tarea, el bodeguero virtual la deja de lado.
- Aprobador virtual: apagado por defecto (`SIM_AUTO_APPROVE=false`). En la presentación las aprobaciones se hacen en vivo.

### 10.3 Guion

`scenarios.yml` define los eventos de la tabla 2.13. Los que dependen de socios externos (rechazo, retraso, alza de precio) se le envían a `partners-mock` al iniciar la simulación.

---

## 11. Frontend

### 11.1 Pantallas

| Pantalla | Contenido | Roles |
|---|---|---|
| Tablero | KPIs, ventas en vivo, inventario por estado, últimas decisiones de la IA, alertas | todos |
| Inventario | Lista con semáforo (quiebre, bajo, normal, exceso), filtros, detalle con kardex y pronóstico | todos |
| Ventas | Pedidos, estados, pedidos retenidos para revisión | manager, admin, viewer |
| Compras | OC por estado, línea de tiempo de cada OC, proveedores, ofertas y precios | purchasing, manager, admin |
| Bodega | Tareas asignadas al usuario, botones grandes de iniciar y terminar, pensada para celular | warehouse |
| Despachos | Envíos, estados, descarga de guías | warehouse, manager, admin |
| IA → Decisiones | Bitácora con datos de entrada, alternativas, razonamiento, resultado de la política y reversión | todos (revertir: manager, admin) |
| IA → Aprobaciones | Bandeja por rol con el razonamiento de la IA y botones de aprobar o rechazar con comentario | purchasing, manager |
| IA → Políticas | Niveles por acción, umbrales, presupuestos, pesos del puntaje, kill switch, modo sombra | admin |
| Asistente | Chat de solo lectura | todos |
| Simulación | Reloj, iniciar, pausar, velocidad, reiniciar, progreso del guion | admin |

### 11.2 Diseño

- Todo color, tamaño y espaciado sale de `tokens.css`. No se escriben colores sueltos en los componentes.
- Tema claro y oscuro con `prefers-color-scheme` y un selector manual.
- Semáforo de inventario con color y también con texto o ícono, para no depender solo del color.
- La barra superior muestra siempre la fecha y hora simuladas y si la IA está activa, en modo sombra o detenida.
- Cada decisión de la IA se muestra con la misma tarjeta en todas las pantallas (`DecisionCard`).
- Montos en formato `$ 1.250.000`; fechas en `dd/mm/aaaa hh:mm`.

---

## 12. Trabajo en equipo

### 12.1 Propiedad de módulos

Cada integrante es dueño de sus módulos: los diseña, los prueba y revisa cualquier cambio que el otro haga en ellos.

| Integrante A: dominio e IA | Integrante B: experiencia, simulación e integración |
|---|---|
| `catalog`, `inventory`, `sales`, `purchasing`, `logistics`, `finance` | `frontend/` completo |
| `ai/*` | `simulation/*`, incluidos los datos demo |
| `db/migration` de sus módulos | `partners-mock/` |
| | `partners` (clientes HTTP y webhooks), `notifications`, `dashboard` |
| **Compartido:** `shared/`, `contracts/`, `AGENTS.md`, `docker-compose.yml`, CI | |

Es una sugerencia. Asignen nombres y ajusten antes de crear el `CODEOWNERS`.

```
# .github/CODEOWNERS (reemplazar USUARIO_A y USUARIO_B por los usuarios de GitHub)
/backend/src/main/java/com/xperp/catalog/        @USUARIO_A
/backend/src/main/java/com/xperp/inventory/      @USUARIO_A
/backend/src/main/java/com/xperp/sales/          @USUARIO_A
/backend/src/main/java/com/xperp/purchasing/     @USUARIO_A
/backend/src/main/java/com/xperp/logistics/      @USUARIO_A
/backend/src/main/java/com/xperp/finance/        @USUARIO_A
/backend/src/main/java/com/xperp/ai/             @USUARIO_A
/backend/src/main/java/com/xperp/simulation/     @USUARIO_B
/backend/src/main/java/com/xperp/partners/       @USUARIO_B
/backend/src/main/java/com/xperp/notifications/  @USUARIO_B
/backend/src/main/java/com/xperp/dashboard/      @USUARIO_B
/backend/src/main/resources/demo/                @USUARIO_B
/partners-mock/                                  @USUARIO_B
/frontend/                                       @USUARIO_B
/backend/src/main/java/com/xperp/shared/         @USUARIO_A @USUARIO_B
/contracts/                                      @USUARIO_A @USUARIO_B
/AGENTS.md                                       @USUARIO_A @USUARIO_B
/docker-compose.yml                              @USUARIO_A @USUARIO_B
/.github/                                        @USUARIO_A @USUARIO_B
```

### 12.2 Cómo trabajar en paralelo sin bloquearse

- Primero el contrato, después el código. B arranca la pantalla con MSW mientras A implementa el endpoint.
- Si B necesita un servicio de A que todavía no existe (por ejemplo, `SalesService` para el generador de demanda), A sube primero la interfaz en `api/` con una implementación provisional que devuelve datos fijos. Luego la reemplaza sin que B cambie nada.
- Las migraciones de Flyway se numeran por bloques para evitar choques: A usa `V100`–`V499` y B usa `V500`–`V899`. `V1`–`V99` es la base compartida.
- Nadie edita un módulo ajeno sin avisar. Si hace falta, se abre el PR y `CODEOWNERS` le pide la revisión al dueño.

### 12.3 Git

- `main` protegida: solo entra por PR, con 1 aprobación, CI en verde, sin force push e historial lineal (squash merge).
- Ramas desde `main`: `feat/<modulo>-<descripcion>`, `fix/...`, `contract/...`, `docs/...`, `chore/...`.
- Commits con Conventional Commits y el módulo como alcance: `feat(purchasing): reasignar OC rechazada`.
- PR pequeños, de menos de 400 líneas cuando se pueda. Antes de abrir el PR se hace rebase sobre `main`.
- Integrar a `main` por lo menos cada 2 o 3 días para que las ramas no se alejen.

Plantilla de PR (`.github/pull_request_template.md`):

```
## Qué cambia
## Módulo(s)
## ¿Toca el contrato? (sí/no, cuál)
## Cómo probarlo
## Capturas (si hay interfaz)
## Checklist
- [ ] Pruebas nuevas o actualizadas
- [ ] La demo sigue funcionando con LLM_PROVIDER=mock
- [ ] AGENTS.md actualizado si cambió algo de lo que describe
```

### 12.4 CI (GitHub Actions)

| Workflow | Se ejecuta si cambian | Pasos |
|---|---|---|
| `contracts.yml` | `contracts/**` | Lint del OpenAPI con Redocly y bundle |
| `backend.yml` | `backend/**`, `contracts/**` | `./mvnw -B verify`: compila contra el contrato, Spotless, pruebas unitarias, de integración con Testcontainers y `ModularityTest` |
| `frontend.yml` | `frontend/**`, `contracts/**` | `npm ci`, `gen:api`, `svelte-check`, ESLint, Vitest, build |

### 12.5 Definición de terminado

- CI en verde.
- Pruebas de la lógica nueva.
- Contrato y `AGENTS.md` al día.
- `docker compose up` y `POST /simulation/reset` siguen dejando la demo funcionando.
- El otro integrante lo revisó.

---

## 13. Convenciones de código

### 13.1 Java

- DTO como `record`. Entidades JPA con Lombok solo para getters y setters.
- Inyección por constructor. Nada de `@Autowired` en campos.
- Nada de lógica de negocio en controllers ni en entidades.
- `@Transactional` en los servicios, no en los controllers.
- Excepciones de negocio propias que se traducen a `{ code, message, details }` en un `@RestControllerAdvice` de `shared`.
- Configuración con `@ConfigurationProperties`. Nada de `@Value` disperso.
- Prohibido `LocalDateTime.now()`, `Instant.now()` o `new Date()` sin el `Clock`.

### 13.2 TypeScript y Svelte

- Svelte 5 con runes (`$state`, `$derived`, `$effect`, `$props`). No se usa la sintaxis anterior (`export let`, `$:`).
- Toda llamada HTTP pasa por `lib/api/client.ts`; los componentes no usan `fetch` directo.
- Los tipos de la API salen de `schema.d.ts`. No se redefinen a mano.
- Estado compartido en archivos `.svelte.ts` dentro de `lib/stores`.

### 13.3 Idioma

Código, tablas, endpoints y commits en inglés. Interfaz, rutas visibles, documentación y razonamientos de la IA en español.

---

## 14. Variables de entorno (`.env.example`)

```env
# General
TZ=America/Bogota
SPRING_PROFILES_ACTIVE=dev,demo

# Base de datos
POSTGRES_DB=xperp
POSTGRES_USER=xperp
POSTGRES_PASSWORD=xperp
DB_URL=jdbc:postgresql://localhost:5432/xperp

# Seguridad
JWT_SECRET=cambiar-por-una-clave-de-al-menos-32-caracteres
JWT_EXPIRES_MINUTES=120

# IA
LLM_PROVIDER=mock                # mock | anthropic | openai | ollama
LLM_MODEL=
LLM_API_KEY=
OLLAMA_BASE_URL=http://localhost:11434
LLM_MAX_CALLS_PER_SIM_DAY=30
AI_SHADOW_MODE=false             # valor inicial; en ejecución manda system_settings

# Simulación
SIM_START_DATE=2026-11-01
SIM_SECONDS_PER_DAY=20
SIM_HISTORY_DAYS=90
SIM_RANDOM_SEED=42
SIM_AUTOSTART=false
SIM_AUTO_APPROVE=false

# Socios simulados
PARTNERS_API_URL=http://localhost:8101
PARTNERS_API_TOKEN=demo-partners-token
WEBHOOK_SECRET=demo-webhook-secret
BACKEND_URL=http://localhost:8080

# Correo
MAIL_HOST=localhost
MAIL_PORT=1025

# Frontend
PUBLIC_API_URL=http://localhost:8080/api/v1
PUBLIC_API_MOCK=false
```

Dentro de Docker Compose los `localhost` cambian por el nombre del servicio (`db`, `partners-mock`, `mailpit`, `backend`).

---

## 15. Puesta en marcha

```bash
cp .env.example .env

# Infraestructura
docker compose up -d db mailpit partners-mock

# Backend (carga la empresa demo la primera vez)
cd backend && ./mvnw spring-boot:run

# Frontend
cd frontend && npm install && npm run gen:api && npm run dev
```

- App: http://localhost:5173 (entrar con `admin@laceiba.demo` / `Xperp2026*`)
- API y Swagger UI: http://localhost:8080/swagger-ui.html
- Correos enviados a proveedores y bodega: http://localhost:8025

Todo en contenedores: `docker compose up -d --build`.

---

## 16. Fases

| Fase | Entregable | Termina cuando | Dueño |
|---|---|---|---|
| 0. Acuerdos | Repo, CI, `CODEOWNERS`, contrato base, migración V1, `shared` | Los dos pueden abrir PR y la CI corre | Ambos |
| 1. Núcleo | Catálogo, inventario, ventas, kardex, auth | Una venta reserva stock y el despacho deja movimiento | A |
| 1. Base visual | Tokens, layout, login, inventario y ventas sobre MSW | Las pantallas funcionan sin backend | B |
| 2. Datos y reloj | Datos demo, historial, reloj, generador de demanda | El tablero muestra ventas solas | B |
| 3. Compras | OC, ofertas, recepciones, presupuestos | Una OC se envía, se confirma y al recibirla sube el stock | A |
| 3. Socios | partners-mock, webhooks, correo | Proveedores y transportadoras responden por webhook | B |
| 4. Reglas y pronóstico | Stock de seguridad, punto de reorden, Holt-Winters | El ciclo diario propone compras correctas | A |
| 5. Logística | Tareas de bodega, guías, traslados, bodeguero virtual | Un pedido sale con guía sin intervención | A + B |
| 6. Agente y políticas | Spring AI, tools, ActionGateway, aprobaciones, bitácora | Ciclo completo autónomo para OC pequeñas | A |
| 6. Centro de IA | Decisiones, aprobaciones, políticas, SSE en vivo | Se aprueba una OC desde la interfaz y se ve el efecto | B |
| 7. Guion y asistente | `scenarios.yml`, asistente, resumen diario | Los 14 escenarios de la tabla 2.13 se cumplen | Ambos |
| 8. Ensayo | `docs/demo-script.md`, ajustes | Demo de 10 minutos sin fallas en modo `mock` y con LLM real | Ambos |

---

## 17. Del MVP a producto comercial

Queda fuera del alcance académico, pero la arquitectura lo permite:

- Registro de empresas, planes y facturación del SaaS (ya existe `company_id` en todo).
- Integraciones reales: facturación electrónica DIAN, API de WhatsApp Business para proveedores y bodega, APIs de transportadoras, correo real.
- Lotes y vencimientos con salida FEFO.
- App de bodega con lector de código de barras.
- Permisos por acción y por bodega, auditoría completa de cambios.
- Separar `ai` en un servicio aparte si la carga lo exige; ya se comunica por eventos y una fachada.

---

## 18. Reglas para agentes de IA en este repo

1. Leer este archivo completo antes de proponer o escribir código.
2. Respetar los límites de módulo: otro módulo solo se usa a través de `api/` y `events/`. `ModularityTest` tiene que pasar.
3. Respetar `web → service → repository`. Ningún controller toca repositorios.
4. Contrato primero: si un cambio necesita un endpoint nuevo o distinto, se modifica `contracts/openapi` en un PR aparte.
5. Las tools del agente llaman servicios a través de `ActionGateway`. Nunca SQL ni repositorios.
6. Toda acción de escritura de la IA queda en `ai_decisions`, aunque quede bloqueada.
7. La hora siempre viene del `Clock` inyectado.
8. Todo cambio de esquema va en una migración de Flyway nueva, dentro del bloque de numeración del dueño. Nunca se edita una migración aplicada.
9. Montos con `BigDecimal` en Java y como string decimal en JSON.
10. Nada de secretos en el código: todo sale de variables de entorno.
11. Los datos demo no usan marcas, empresas ni personas reales.
12. La demo tiene que seguir funcionando con `LLM_PROVIDER=mock`.
13. Entregar archivos completos, uno por uno, con la ruta exacta y cómo se integran.
14. Pruebas en el mismo cambio para reglas, políticas, puntajes, pronóstico y tools.
15. No agregar dependencias sin dejar el motivo en `docs/decisions/`.
16. Si la tarea toca módulos de los dos integrantes, proponer el plan antes de escribir código.
17. Si algo de este documento no cuadra con la tarea, señalarlo en vez de improvisar.
