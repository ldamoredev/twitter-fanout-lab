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

## S3 — El híbrido

Aprendizaje: **el umbral no separa usuarios importantes de usuarios comunes, separa dos costos**. Por debajo, publicar paga una escritura por seguidor y leer es gratis; por encima, publicar no escribe nada y cada lectura de cada seguidor paga una consulta extra. El umbral es el punto donde el burst de escritura de un post empieza a doler más que esa consulta.

```
publicar   seguidores <= 10.000  → fan-out (S2): 1 + ceil(n/100) jobs, n escrituras
           seguidores >  10.000  → nada: FanoutPost cuenta, no despacha y termina
leer       timeline precomputado ∪ últimos 50 posts de cada celebridad que seguís
           merge por PostId, sin hidratar
```

### De dónde sale el número

`CELEBRITY_THRESHOLD_FOLLOWERS = 10_000`, y esta es la defensa:

- **Por qué no más alto.** Un post cuesta `1 + ceil(seguidores/100)` jobs. En el umbral son 101 jobs y, al ritmo que midió S2 (~14 ms por cada 1.000 escrituras in-memory), ~140 ms hasta que el último seguidor lo tiene. Un millón de seguidores serían 10.001 jobs de un solo post: un burst que la cola no absorbe sin retrasar a todos los demás.
- **Por qué no más bajo.** Cada celebridad que seguís agrega una consulta a **todas** tus lecturas, y las lecturas son el camino caliente. Con el umbral en el piso todos son celebridades y leer vuelve a costar O(seguidos), que es exactamente el fan-out on read que S1 descartó.
- **Por qué 10.000 en particular.** Deja el conjunto de celebridades chico — en Twitter real, bastante menos del 1% de las cuentas — así que un lector típico mergea pocas fuentes, y a la vez ningún post genera un burst que la cola no drene en menos de un segundo. El número exacto no sale de la teoría: sale de medir a qué ritmo drena la cola de producción, y por eso es configurable.

Celebridad es estar **por encima** del umbral: justo en 10.000 todavía hay fan-out. El test `un autor con exactamente el umbral todavia dispara fan-out` fija esa decisión para que no dependa de leer el `>`.

### Configurable de verdad

El umbral es `CelebrityThreshold`, un servicio del contenedor y no una constante suelta:

```bash
TRANTOR__FANOUT__CELEBRITY_THRESHOLD_FOLLOWERS=50 ./build/install/twitter-fanout-lab/bin/twitter-fanout-lab
```

o `{"fanout": {"celebrityThresholdFollowers": 50}}` en `settings.json`. El test `el umbral se baja por configuracion sin recompilar` lo prueba contra el proceso real: con el umbral en 2, un autor con 3 seguidores publica y la cola recibe **un** job (el reparto) y ningún chunk.

### Qué hace Trantor acá (leído y corrido)

- Configurar un valor propio se hace con `@ConfigValue("fanout.celebrityThresholdFollowers")` sobre un parámetro del constructor, que resuelve `ConfigServiceValueResolver` de `trantor-di`. Si el parámetro no tiene default, la app **no arranca** sin la variable; y sólo convierte primitivos. Ver `FRICCION.md`.
- El nombre de la variable de entorno sale de `EnvironmentVariablesConfigProvider.underscoreToCamelCase`: parte por `__` y camelCasea cada tramo.
- El merge ordena por `PostId` sin hidratar porque los `Id` de Trantor son UUIDv7 (`UuidCreator.getTimeOrderedEpoch()`): el timestamp está en los bits altos. `Id` no implementa `Comparable`, así que hay que ordenar por `toUUID()`. Ver `FRICCION.md`.
- `FanoutPost` cuenta seguidores antes de listarlos (`Follows.followersCount`, separado de `followersOf` a propósito): decidir si alguien es celebridad no puede costar traer 50 millones de ids. En Postgres eso es un `count`, no un `select`.

```bash
./lab
# /hibrido.html — la regla, la calculadora de los dos costos y la demo
```

Panel: `/hibrido.html` explica los dos caminos y calcula, para un número de seguidores, cuántos timelines se escriben al publicar contra cuántos ids hay que mergear al leer.

Tests S3: **10** (7 del híbrido con dobles, 2 end-to-end con el umbral bajado por config, 1 del panel). Suite: **37 JUnit + 11 vitest**.

**Sin hacer, a propósito:** el feed seguía devolviendo ids pelados — hidratar, y que el autor vea su propio post antes que el resto, es S4. La ventana de pull por celebridad (`CELEBRITY_MERGE_POSTS = 50`) no pagina. Y un autor que cruza el umbral entre publicar y leer deja el post en los dos lados: el merge lo deduplica, pero nadie limpia lo ya escrito.

## S4 — Hidratación y read-your-writes

Aprendizaje: **el autor y el seguidor no leen el mismo camino**. Publicar cachea el snapshot y contesta 201. El autor mergea sus posts recientes al hidratar: el texto está en el feed cuando el request ya volvió. El seguidor lee el timeline precomputado, que el fan-out llena después. Son dos consistencias, no un bug.

```
publicar   persistir + cache.put + PostPublished (adentro de defer)
           al salir del bloque, el evento despacha FanoutPost
leer autor      precomputado ∪ posts propios ∪ celebridades  → hidratar desde cache
leer seguidor   precomputado ∪ celebridades                   → hidratar desde cache
```

`defer` no es async. Bufferiza `EventDispatcher.publish` hasta que el bloque termina, y recién ahí corre los handlers. El publish del evento va *antes* de persistir, a propósito: sin `defer`, el handler vería el store vacío. `jobs.dispatch` adentro de `defer` **no** se atrasa — por eso el fan-out sale del evento y no del command.

### Qué hace Trantor acá (leído y corrido)

- `CacheModule` (lo registra `ApplicationBuilder` solo) da `InMemoryCacheFactory`. No te inyecta un `InMemoryCache<K,V>`: hay que llamar `factory.create(...)` y registrar el servicio. Crafty lo hace adentro del repositorio; acá es `PostCache`.
- El cache tiene L1 (ThreadLocal, por tx) y L2 (Caffeine). Sin tx activa — el lab usa `NullTransactionManager` — `put` va directo a L2. El comentario del framework es explícito: guardar snapshots, nunca entidades mutables. Ver `FRICCION.md`.
- Defaults: `expireAfter = 1.minutes`, `maximumSize = 1000`. Un minuto es corto para un lab abierto, y 1000 entradas no cubren una ventana de 800. El lab los pisa a 1 hora / 10.000.
- `EventDispatcher.defer` sólo mira `publish`. Si el command despachara `FanoutPost` directo, envolverlo en `defer` no cambiaría nada.

```bash
./lab
# /lectura.html — los dos caminos, la calculadora de quién ve el post, y la demo
AUTHOR=$(uuidgen | tr '[:upper:]' '[:lower:]')
curl -s -X POST localhost:18080/posts -H 'content-type: application/json' \
  -d "{\"authorId\":\"$AUTHOR\",\"text\":\"lo mio ya\"}"
curl -s localhost:18080/timelines/$AUTHOR
# {"posts":[{"postId":"...","authorId":"...","text":"lo mio ya"}]}
```

Panel: `/lectura.html` explica los dos caminos y calcula, para un elapsed y una latencia de fan-out, quién ya ve el post.

Tests S4: **10** (6 de hidratación con dobles, 2 de `defer`, 1 HTTP del autor, 1 del panel) más el contrato del feed que ahora trae texto. Suite: **46 JUnit + 15 vitest**.

**Sin hacer, a propósito:** si el proceso se cae entre persistir y disparar el evento, el fan-out se pierde. Eso es el outbox (S5). El cache no se invalida: los posts no se editan. Y `Identity` del CQBus sigue sin usarse — el “autor” es el `userId` del timeline, no un caller autenticado.

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
