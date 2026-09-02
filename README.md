# twitter-fanout-lab

Laboratorio backend de fan-out híbrido (problema de la celebridad) sobre Trantor `0.8.1-beta11`. No es un clon de Twitter. El código es el experimento; `FRICCION.md` es el feedback al framework.

## S0 — Hello world y el apagado

Aprendizaje: arrancar Trantor sin README costó **12 minutos y 8 pasos** desde `gradle init` hasta el primer HTTP 200. El costo no es Gradle: es adivinar el layout, el puerto y el ciclo de vida.

Pasos, con el reloj encima:

1. `gradle` no está en PATH. No instalé Gradle en la máquina: copié el wrapper de `~/Documents/projects/404/trantor` (Gradle 9.4.0).
2. Primer `./gradlew`: 17 s para bajar la distribución.
3. `./gradlew init --type kotlin-application`: 1 s. Genera `app/src/main/kotlin`, Kotlin 2.3.0 y Guava. Trantor no se parece a eso.
4. Leer el código de Trantor (no hay README): `src/` + `test/` + `resources/`, plugin `dev.botta.kotlin-conventions` 0.4.2, Kotlin 2.3.10, toolchain 25, BOM `0.8.1-beta11`.
5. Tirar el layout de `init` y armar el build de consumidor.
6. Test en rojo: `GET /health` → 404.
7. `Ping` (query en el CQBus) + `settings.json` porque el puerto default de `HttpServer` es **80**.
8. Primer 200: test in-process, y después el proceso real con `{"status":"ok"}`.

El `HostedService` que arranca y para es el `HttpServer` de Trantor. In-process, `Host.stop()` y `Host.run()` + `lifetime.stopApplication()` paran el servicio (timeout default: 30 segundos). Con SIGTERM de verdad el JVM sale 143 en ~1 s **sin** logs de `Stopping host`: el shutdown hook sólo llama `stopApplication()`, que no llama `stop()`. Ver `FRICCION.md`.

Correr:

```bash
./lab test
./lab
curl http://127.0.0.1:18080/health
```

`./lab` fija `JAVA_HOME` en el JDK 25 e ignora el del shell: `kotlin-conventions` 0.4.2 ni resuelve con un JVM menor a 21, y el default de macOS suele ser Zulu 17.

Tests S0: **3 passed**.

## S1 — El modelo

Aprendizaje: el timeline precomputado guarda IDs porque 50 millones de usuarios × 800 entradas son **640 GB** de UUID, no **12.8 TB** de post completo. El fan-out (S2) copia 16 bytes; el texto se hidrata después (S4).

### Cálculo

Constantes en `TimelineStorage.kt`: `PRECOMPUTED_TIMELINE_USERS = 50_000_000`, `TIMELINE_WINDOW_POSTS = 800`, `POST_ID_BYTES = 16`, `FULL_POST_BYTES = 320`.

`320` es un registro de post guardado: 16 (`PostId`) + 16 (`UserId`) + 280 (`MAX_POST_TEXT_CHARS`, 1 byte/char) + 8 (`createdAt` en millis).

```
50_000_000 × 800 × 16  = 640_000_000_000 bytes   = 640 GB    (sólo IDs)
50_000_000 × 800 × 320 = 12_800_000_000_000 bytes = 12.8 TB  (post completo)
```

12.8 TB / 640 GB = **20×**. El test `cincuenta millones de timelines de ids pesan 640 GB y con el post completo 12 punto 8 TB` fija esos dos números.

### Qué hace Trantor acá (leído y corrido)

`ApplicationController` no tiene lógica: es un mapeo URL → request del CQBus. `ApplicationRouteRegister.handleRequest` deserializa y llama `executor.execute`. Lo vimos en `trantor-web/.../ApplicationRoutes.kt` y lo corrimos:

- `POST /posts` → `PublishPost` → 201 con `{"postId":"<uuid>"}`. Test: `un post publicado por http se lee por id`.
- `GET /posts/{postId}` → `GetPost`. El path param se copia al JSON con el **mismo nombre** que el campo del query (`PathParamApplicationRequestMapperJsonTransformer`). `{postId}` tiene que llamarse `postId`.
- Cada subclass de `Id` necesita `constructor(UUID)`: `IdTypeAdapterFactory.read` hace `idClass.getConstructor(UUID::class.java)`. `UserId` y `PostId` lo tienen. Sin eso Gson no arma el request.
- `NotFoundError` responde **404** aunque `WebApplication` también registre `DomainError` como 400. Test: `un post que no existe responde 404`.
- `GET /timelines/{userId}` serializa `{"postIds":[...]}` y **no** el texto. Test: `el timeline por http expone ids y no el texto del post`. En S1 nadie escribe el timeline al publicar: el test prepende a mano. Eso es S2.

Rutas: `POST /posts` (201), `GET /posts/{postId}`, `POST /follows`, `GET /timelines/{userId}`.

```bash
AUTHOR=$(uuidgen | tr '[:upper:]' '[:lower:]')
curl -s -X POST http://127.0.0.1:18080/posts \
  -H 'content-type: application/json' \
  -d "{\"authorId\":\"$AUTHOR\",\"text\":\"hola lab\"}"
# {"postId":"..."}
```

Tests S1 (modelo): **10 passed** (6 CQBus + 1 cálculo + 3 HTTP).

### El panel

Aprendizaje: **trantor-web no sirve archivos estáticos**. No hay `staticFiles` en el módulo. La única puerta es `HttpServerSettings.configureJavalin`, el JavalinConfig crudo. El panel se escribe en `panel/` (React + TypeScript + Vite) y el build escupe `resources/public`, que se monta con `Location.CLASSPATH` para que sobreviva a `installDist`. Ver `FRICCION.md`.

Dos páginas, nada más:

- `/` — la respuesta de entrevista: dónde se paga el costo, fan-out on write, on read, el híbrido, consistencia distinta por camino. Sin botones de lo que no existe.
- `/modelo.html` — calculadora en vivo (defaults de `TimelineStorage.kt`) y tres botones contra la API real: publicar, seguir, pedir timeline.

```bash
./lab
# http://127.0.0.1:18080/
```

`./lab` corre Vite + `installDist` + el proceso, con el JDK 25 fijo. `./lab test` es `./gradlew test` (incluye `npm test` del panel). Para iterar sólo el UI: el JVM en 18080 y `npm --prefix panel run dev` (proxy a la API).

Tests del panel: **3 HTTP** (JUnit) + **3 vitest** (cálculo). Suite al cierre de S1: **16 JUnit passed**.

S1 cerrado.

## S2 — Fan-out on write

Aprendizaje, con el número adelante: un post de alguien con **1.000 seguidores** son **11 jobs** y **1.000 escrituras**. Publicar contestó en **7 ms**; el fan-out completo terminó **14 ms** después de que el cliente ya tenía su 201. El costo de escritura no desaparece — se corre del request a la cola.

```
publish (request)          POST /posts → 201, despacha 1 job
  FanoutPost               lee seguidores, corta en tandas de 100 → 10 jobs
    WriteTimelineChunk×10  prepend del PostId en 100 timelines cada uno
jobs = 1 + ceil(1000/100) = 11        escrituras = 1000
```

Los dos niveles son la decisión del slice. Si `PublishPost` leyera la lista de seguidores, publicar costaría O(seguidores) **antes** del 201; si despachara un job por seguidor, serían 1.000 mensajes de cola para 1.000 escrituras de 16 bytes. La tanda es la perilla: `FANOUT_CHUNK_FOLLOWERS = 100`, `FANOUT_WORKERS = 8` en `FanoutTuning.kt`.

Decisión explícita: el fan-out escribe el timeline de los **seguidores**, no el del autor. Twitter mete tus propios posts en tu home timeline; acá no, porque el brief define el trabajo como "el ID en el timeline de cada seguidor" y meter al autor ensucia la cuenta de jobs.

### Qué hace Trantor acá (leído y corrido)

- `JobsModule` (lo registra `ApplicationBuilder` solo) da `JobDispatcher`, `JobQueueRegistry`, `JobHandlerRegistry` y el serializer. El consumidor **no** viene incluido: hay que llamar `services.addJobProcessor(...)`.
- `JobProcessor` es un `HostedService`. Es el **segundo** del lab, después del `HttpServer`, y ahí el orden de apagado deja de ser trivia: se paran en orden inverso, así que primero muere el HTTP y después drena el worker.
- La cola es del lab. `MessageQueue` sólo tiene una implementación publicada y es SQS (`trantor-queues-sqs`). `InMemoryMessageQueue` copia su semántica: entrega al menos una vez, visibility timeout, borrado explícito. Ver `FRICCION.md`.
- Los jobs son `data class ...: Job()` y se serializan con Gson: los `Id` viajan como string por `IdTypeAdapterFactory`, igual que en HTTP.
- Métricas: no hay. El `HttpServer` tiene `stats`; el `JobProcessor` no tiene nada. Los contadores salen de la cola y se exponen en `GET /metrics/fanout` → `{"jobsEnqueued":11,"jobsProcessed":11,"jobsPending":0}`.

```bash
./lab
ALICE=$(uuidgen | tr '[:upper:]' '[:lower:]'); BOB=$(uuidgen | tr '[:upper:]' '[:lower:]')
curl -s -X POST localhost:18080/follows -H 'content-type: application/json' \
  -d "{\"followerId\":\"$ALICE\",\"followeeId\":\"$BOB\"}"
curl -s -X POST localhost:18080/posts -H 'content-type: application/json' \
  -d "{\"authorId\":\"$BOB\",\"text\":\"S2 escribe timelines\"}"
curl -s localhost:18080/timelines/$ALICE   # {"postIds":["..."]} sin tocar nada más
curl -s localhost:18080/metrics/fanout     # {"jobsEnqueued":2,"jobsProcessed":2,"jobsPending":0}
```

`./lab bench` corre `FanoutThroughputTest` con los streams prendidos y escupe la línea de la medición. El número es el piso: todo en memoria, en la misma JVM, sin red ni Postgres.

Panel: `/fanout.html` cuenta la cadena, calcula jobs contra seguidores y lee `/metrics/fanout` en vivo.

Tests S2: **11** (4 de la cola, 4 de los jobs con dobles, 2 end-to-end con el `JobProcessor` real, 1 del panel). Suite: **27 JUnit + 6 vitest**.

**Sin hacer, a propósito:** una celebridad con 50 millones de seguidores son 500.001 jobs y 50 millones de escrituras por post — el umbral es S3. Los reintentos existen (visibility timeout) pero no hay dead letter ni backoff. El timeline sigue devolviendo ids pelados: hidratar es S4.

## Cómo está armado

Monolito de un solo contexto (el feed). La costura que se puede partir después es **API vs worker**, no posts vs follows.

```
web/       TwitterFanoutWebModule + controllers   HTTP
core/      CoreModule                             un composition root
           posts/ follows/ timelines/ health/     packages, no módulos de Trantor
platform/  queues/                                adaptadores de Trantor (la cola del lab)
panel/     React + Vite                           fuente del UI; dist → resources/public
```

`main` cuelga el web module. El web registra controllers y, en `compose`, mete `CoreModule`. El core no habla Javalin ni colas: en `compose` registra stores, la cola y el `JobProcessor`; en `initialize`, los handlers del CQBus y los de jobs. `platform/` guarda lo que implementa una interfaz de Trantor: hoy `InMemoryMessageQueue`, mañana Postgres.
