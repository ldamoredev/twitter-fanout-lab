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
./gradlew test
./gradlew installDist
TRANTOR__HTTP_SERVER__PORT=18080 ./build/install/twitter-fanout-lab/bin/twitter-fanout-lab
curl http://127.0.0.1:18080/health
```

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

Tests S1: **10 passed** (6 CQBus + 1 cálculo + 3 HTTP). Suite al cierre de S1: **13 passed**.

## S1.5 — El panel

Aprendizaje: **trantor-web no sirve archivos estáticos**. No hay `staticFiles` en el módulo. La única puerta es `HttpServerSettings.configureJavalin`, el JavalinConfig crudo. El panel vive en `resources/public` y se monta con `Location.CLASSPATH` para que sobreviva a `installDist`. Ver `FRICCION.md`.

Dos páginas, nada más:

- `/` — la respuesta de entrevista: dónde se paga el costo, fan-out on write, on read, el híbrido, consistencia distinta por camino. Sin botones de lo que no existe.
- `/modelo.html` — calculadora en vivo (defaults de `TimelineStorage.kt`) y tres botones contra la API real: publicar, seguir, pedir timeline.

```bash
./gradlew installDist
TRANTOR__HTTP_SERVER__PORT=18080 ./build/install/twitter-fanout-lab/bin/twitter-fanout-lab
# http://127.0.0.1:18080/
```

Tests S1.5: **3 passed**. Suite: **16 passed**.

## Cómo está armado

Monolito de un solo contexto (el feed). La costura que se puede partir después es **API vs worker**, no posts vs follows.

```
web/     TwitterFanoutWebModule + controllers     HTTP
core/    CoreModule                               un composition root
         posts/ follows/ timelines/ health/       packages, no módulos de Trantor
```

`main` cuelga el web module. El web registra controllers y, en `compose`, mete `CoreModule`. El core no habla Javalin: en `compose` registra stores, en `initialize` registra todos los handlers del CQBus. `platform/` aparece cuando haya cola o Postgres.
