# Registros de decisiones de arquitectura (ADR)

Cada decisión de arquitectura y cada dependencia nueva se registra aquí con su motivo (`AGENTS.md`, regla 15).

## Cómo escribir un ADR

1. Copia `0000-plantilla.md` como `NNNN-titulo-corto.md`, usando el siguiente número libre.
2. Llénalo en español: contexto, decisión y consecuencias.
3. Súbelo en el mismo PR que el cambio y agrega una línea en `CHANGELOG.md`.
4. Un ADR aceptado no se reescribe. Si la decisión cambia, se crea uno nuevo que lo reemplaza y el anterior se marca como `Reemplazado por NNNN`.

## Índice

| N.º | Título | Estado |
|---|---|---|
| [0001](0001-stack.md) | Stack tecnológico y versiones | Aceptado |
| [0002](0002-esqueleto-del-repositorio.md) | Decisiones del esqueleto del repositorio | Propuesto |
