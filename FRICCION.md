# FRICCION.md

Bitácora de consumidor de Trantor 0.8.1-beta11. Una entrada por cada cosa que hizo parar.

## gradle no está en PATH: el primer comando del brief es imposible

- Slice: S0
- Módulo: (fuera de Trantor)
- Qué intentaba hacer: `gradle init` como punto de partida del laboratorio.
- Qué esperaba que pasara: tener Gradle en el PATH, o que Trantor documentara el wrapper.
- Qué pasó: `gradle: command not found`. JAVA_HOME default era Zulu 17; JDK 25 sí estaba instalado (`corretto-25.0.4`).
- Cómo lo resolví, o si no lo resolví: no instalé Gradle en la máquina. Copié `gradlew` + `gradle/wrapper` del clone de Trantor (9.4.0). El primer `./gradlew` tardó 17 s en bajar la distribución.
- Cuánto me costó: 5 minutos
- El caso mínimo que lo reproduce: `command -v gradle` en una máquina sin Gradle global; repo consumidor vacío.

## gradle init genera un proyecto que Trantor no puede tragar

- Slice: S0
- Módulo: trantor-core / kotlin-conventions
- Qué intentaba hacer: un hello world Kotlin listo para colgarle Trantor.
- Qué esperaba que pasara: que `init --type kotlin-application` se pareciera al consumidor (Crafty) o que un README dijera el layout.
- Qué pasó: `app/src/main/kotlin`, version catalog con Kotlin 2.3.0 y Guava. Trantor usa Kotlin 2.3.10, toolchain 25, plugin `dev.botta.kotlin-conventions` 0.4.2 y fuentes en `src/`, `test/`, `resources/` (sin `src/main/kotlin`). No hay README, CHANGELOG ni docs en ningún módulo.
- Cómo lo resolví, o si no lo resolví: tiré el layout de `init` y copié las convenciones leyendo `trantor/build.gradle.kts` y Crafty. Una vez aplicado el plugin, `src/` y `resources/` compiló sin pelea.
- Cuánto me costó: 20 minutos
- El caso mínimo que lo reproduce:
  ```
  ./gradlew init --type kotlin-application --dsl kotlin --java-version 25
  # comparar con trantor-core/src/... y api/build.gradle.kts de Crafty
  ```

## HttpServer arranca en el puerto 80 y settings.json no está documentado

- Slice: S0
- Módulo: trantor-web
- Qué intentaba hacer: un GET que responda 200 en local.
- Qué esperaba que pasara: un default de desarrollo (8080) o un ejemplo de `settings.json`.
- Qué pasó: `HttpServerSettings.port` default es 80. El 8080 que puse en `resources/settings.json` estaba ocupado por Docker. No hay ejemplo publicado.
- Cómo lo resolví, o si no lo resolví: `settings.json` con `httpServer.port`, y override `TRANTOR__HTTP_SERVER__PORT=18080`. El override **sí funciona**: Javalin escuchó en 18080. Eso es dato para S6.
- Cuánto me costó: 8 minutos
- El caso mínimo que lo reproduce:
  ```kotlin
  WebApplication.builder { appName = "x" }.build().start()
  // bind a :80, o a lo que diga settings.json sin aviso
  ```

## SIGTERM no llama HostedService.stop(): el hook sólo hace notifyStopping

- Slice: S0
- Módulo: trantor-hosting
- Qué intentaba hacer: un `HostedService` que arranque y pare limpio con SIGTERM.
- Qué esperaba que pasara: SIGTERM → `HttpServer.stop(30)` → logs `Stopping host` / `Stopping service HttpServer` → exit 0.
- Qué pasó: in-process, `Host.stop()` y `Host.run()` + `lifetime.stopApplication()` sí paran el servicio (3er test). Con SIGTERM real al JVM (`kill -TERM <pid>`): exit 143 en 1.041 s, **cero** líneas de stop en el log, el puerto se cierra porque muere el proceso. `DefaultHost` registra `Runtime.addShutdownHook { lifetime.stopApplication() }`. `stopApplication()` sólo hace `notifyStopping()`. `Host.run()` espera ese signal en el thread main y **después** llama `stop()`. Cuando el hook termina, el JVM sigue el shutdown y no espera a que main ejecute `stop()`.
- Cómo lo resolví, o si no lo resolví: no lo resolví. Lo dejé: es el comportamiento que Kubernetes va a negociar en S6. Los tests cubren el camino in-process, que es el que Trantor sí implementó.
- Cuánto me costó: 15 minutos
- El caso mínimo que lo reproduce:
  ```
  TRANTOR__HTTP_SERVER__PORT=18080 ./build/install/twitter-fanout-lab/bin/twitter-fanout-lab
  kill -TERM $(pgrep -f lab.fanout.MainKt)
  # exit 143, sin "Stopping host" en el log
  ```
  Contraste: el test `SIGTERM via stopApplication desbloquea run y para los servicios` pasa porque el JVM no está en shutdown y el thread de `run()` sí alcanza a llamar `stop()`.

## IntelliJ sincroniza Gradle con Java 17 y kotlin-conventions exige 21

- Slice: S0
- Módulo: kotlin-conventions (plugin de Trantor, no un módulo del BOM)
- Qué intentaba hacer: abrir el lab en IntelliJ y sincronizar Gradle.
- Qué esperaba que pasara: que `jvmToolchain(25)` bastara, o que el IDE tomara el JDK 25 que ya está instalado.
- Qué pasó: `Could not resolve dev.botta:kotlin-conventions:0.4.2` porque `Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.` El toolchain sólo aplica al compile; el daemon de Gradle (el JVM de IntelliJ) carga el plugin y sigue en 17. Es el mismo default Zulu 17 del PATH.
- Cómo lo resolví, o si no lo resolví: Gradle JVM y Project SDK a `corretto-25` (el mismo que Crafty). `.idea/` está en gitignore: hay que setearlo en cada máquina. Lo mismo pasa en la terminal: el `JAVA_HOME` default de macOS es Zulu 17 y el build muere en la fase de configuración. Por eso `./lab` **pisa** `JAVA_HOME` con `/usr/libexec/java_home -v 25` en vez de respetar el del shell.
- Cuánto me costó: 5 minutos, más 5 la segunda vez (la misma falla desde zsh, con `./lab`).
- El caso mínimo que lo reproduce: abrir el proyecto en IntelliJ con Gradle JVM = 17; sync. O `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test` → `Dependency requires at least JVM runtime version 21`.

## Nested addModule funciona igual que Crafty: compose al registrar, initialize al build

- Slice: S0
- Módulo: trantor-hosting
- Qué intentaba hacer: `TwitterFanoutWebModule` → `CoreModule` → módulos de feature, como CraftyWebModule → CoreModule.
- Qué esperaba que pasara: que `compose` anidado registrara DI y que `initialize` de todos corriera antes del primer request.
- Qué pasó: sin fricción. `addModule` llama `compose` en el acto. `ApplicationBuilder.build()` hace `getAll<Module>()` e `initialize` de cada uno. Los controllers se registran antes que los handlers y no importa: las rutas no ejecutan el bus hasta que llega un request.
- Cómo lo resolví, o si no lo resolví: no hubo nada que resolver. Después colapsé los módulos de feature (`PostsModule`, etc.) en un solo `CoreModule`: `addModule` anidado funciona, pero posts/follows/timelines no son bounded contexts. Quedó `web` → `core` (un composition root) y packages Kotlin adentro.
- Cuánto me costó: 10 minutos (el nested addModule). El colapso a un CoreModule: 15 minutos, sin pelea de Trantor.
- El caso mínimo que lo reproduce: `TwitterFanoutWebModule.compose { addModule<CoreModule>() }`. Un segundo `addModule` adentro de `CoreModule` también corre `compose` al registrar e `initialize` al `build()`.

## Cada Id necesita constructor(UUID): Gson no usa el constructor String del padre

- Slice: S1
- Módulo: trantor-gson / trantor-web
- Qué intentaba hacer: `GET /posts/{postId}` y `POST /posts` con `authorId` en el body, mapeados a `GetPost` / `PublishPost` por `ApplicationController`.
- Qué esperaba que pasara: que `Id(raw: String)` del padre bastara, o que hubiera un README del mapper HTTP → CQBus.
- Qué pasó: no hay docs. `ApplicationRequestMapper` junta body JSON + path params y Gson deserializa el `Request`. `IdTypeAdapterFactory.read` no llama al constructor String: hace `idClass.getConstructor(UUID::class.java).newInstance(UUID.fromString(str))`. El path param tiene que llamarse igual que el campo (`{postId}` → `GetPost.postId`); `PathParamApplicationRequestMapperJsonTransformer` copia la clave tal cual.
- Cómo lo resolví, o si no lo resolví: `UserId` y `PostId` con `constructor(UUID)` desde el primer commit de S1, leyendo el adapter. No llegué a romperlo en runtime. Los tests HTTP de S1 pasan con ese constructor.
- Cuánto me costó: 12 minutos (leer `IdTypeAdapterFactory`, `ApplicationRequestMapper` y el transformer de path).
- El caso mínimo que lo reproduce:
  ```kotlin
  class PostId(rawId: UUID = UuidCreator.getTimeOrderedEpoch()): Id(rawId)
  // IdTypeAdapterFactory.read:
  //   val ctor = idClass.getConstructor(UUID::class.java)
  // Sin ese constructor: NoSuchMethodException → JsonParseException → 400
  http.get<GetPost>("/posts/{postId}") // el nombre del param es el del campo
  ```

## CQBus: comandos y queries del modelo sin pelea

- Slice: S1
- Módulo: cqbus / trantor-core
- Qué intentaba hacer: `PublishPost`, `GetPost`, `FollowUser`, `GetTimeline` como `Command`/`Query` con handler `internal`, registrados en `CoreModule.initialize`.
- Qué esperaba que pasara: que el bus ejecutara el handler y devolviera el result.
- Qué pasó: sin fricción. `bus.registerHandler { services.create<PublishPost.Handler>() }` y `bus.execute(...)` en test, y el mismo camino por HTTP vía `WebApplicationExecutor`. `ArgumentCannotBeEmptyError` / `InvalidArgumentError` / `NotFoundError` de `trantor-domain` se mapean solos (`DomainError` → 400, `NotFoundError` → 404).
- Cómo lo resolví, o si no lo resolví: no hubo nada que resolver.
- Cuánto me costó: 0 minutos de pelea con el bus.
- El caso mínimo que lo reproduce: `CQBus().registerHandler { PublishPost.Handler(posts) }; bus.execute(PublishPost(authorId, "hola lab"))`.

## trantor-web no sirve estáticos: la única forma es configureJavalin

- Slice: S1.5
- Módulo: trantor-web
- Qué intentaba hacer: servir el panel (`resources/public`) en el mismo proceso que la API, y que sobreviva a `installDist`.
- Qué esperaba que pasara: un `staticFiles` en `HttpServer` o en `WebApplication`, como tiene Javalin de fábrica.
- Qué pasó: `rg staticFiles` en `trantor-web` no encuentra nada. Lo que sí hay es `HttpServerSettings.configureJavalin: (JavalinConfig) -> Unit = {}`, y `HttpServer` lo llama adentro de `Javalin.create` **antes** de `start`. El lambda no entra por `settings.json`. Hay que mutar el settings con `services.configure<HttpServerSettings>` en `compose`, antes de que el DI construya el `HttpServer`.
- Cómo lo resolví, o si no lo resolví: escape hatch, a propósito:
  ```kotlin
  services.configure<HttpServerSettings> { settings, _ ->
      settings.configureJavalin = { javalin ->
          javalin.staticFiles.add("/public", Location.CLASSPATH)
      }
  }
  ```
  `Location.CLASSPATH` + `resources/public` (el dist de Vite) es lo que hace que `installDist` siga sirviendo `/` y `/modelo.html`.
- Cuánto me costó: 14 minutos (confirmar que no hay staticFiles, leer `HttpServer` / `HttpServerSettings`, dar con `ServiceRegistry.configure` para llegar antes de la construcción del server).
- El caso mínimo que lo reproduce: `rg staticFiles ~/Documents/projects/404/trantor/trantor-web` → 0 hits. `HttpServer.kt` línea del `settings.configureJavalin(javalinConfig)`. Sin ese seteo, `GET /` es 404.

## CLASSPATH no ve panel/: el dist tiene que existir antes de processResources

- Slice: S1
- Módulo: trantor-web (el mismo escape hatch; el resto es el lab)
- Qué intentaba hacer: escribir el panel en React+TS+Vite y seguir sirviéndolo desde el mismo proceso que la API.
- Qué esperaba que pasara: que `configureJavalin` pudiera apuntar a un directorio fuera del jar, o que hubiera un hook de estáticos en desarrollo.
- Qué pasó: `Location.CLASSPATH` sólo ve lo que Gradle empaquetó. Editar `panel/src` no cambia lo que escucha Javalin hasta que Vite escriba `resources/public` y corra `processResources` / `installDist`. El daemon de Gradle tampoco hereda nvm: sin Node en PATH, `panelBuild` ni arranca.
- Cómo lo resolví, o si no lo resolví: `panel/` es la fuente. Vite `outDir` = `resources/public` (gitignored). `processResources` depende de `panelBuild`; `panelBuild` corre `scripts/with-node.sh npm run build`. `./lab` hace Vite + `installDist` + el proceso. Para iterar el UI sin reempaquetar: `npm --prefix panel run dev` con proxy a `:18080`.
- Cuánto me costó: 25 minutos (MPA de Vite, tarea Gradle, PATH de nvm, tests HTTP que ahora leen el bundle en vez del HTML).
- El caso mínimo que lo reproduce: borrar `resources/public`, `./gradlew test` sin Node en PATH → Exec falla. Con Node, el test `la portada del panel se sirve en la raiz` busca el copy en el JS de `/assets/`, no en el HTML.

## La única MessageQueue publicada es SQS: sin AWS, el fan-out no arranca

- Slice: S2
- Módulo: trantor-core/queues + trantor-queues-sqs
- Qué intentaba hacer: fan-out on write con `JobDispatcher` + `JobProcessor`, corriendo en un proceso local y en los tests.
- Qué esperaba que pasara: una implementación in-memory de `MessageQueue` para desarrollo y test, como tienen casi todos los frameworks con colas (aunque sea una marcada "no usar en producción").
- Qué pasó: `rg ": MessageQueue" --glob '*.kt'` en todo el repo devuelve **un** implementador: `SqsQueue.kt:22`. El resto de los hits son parámetros y tipos de retorno. `settings.gradle.kts` incluye un solo módulo de colas. `JobQueueRegistry.getQueue()` hace `error("There are no registered queues")` si no registrás ninguna, así que sin AWS (o LocalStack) no hay jobs. `JobsModule` tampoco registra el consumidor: da dispatcher, registry y serializer, y el `JobProcessor` lo tenés que colgar vos con `addJobProcessor()`.
- Cómo lo resolví, o si no lo resolví: escribí `platform/queues/InMemoryMessageQueue.kt` (60 líneas, `DelayQueue` + mapa de in-flight) copiando la semántica de SQS: entrega al menos una vez, visibility timeout, borrado explícito, `delaySeconds`. Se registra con `services.configure<JobQueueRegistry> { it.addQueue("fanout", queue) }`, que es el mismo camino que usa `addSqsQueue()`.
- Cuánto me costó: 35 minutos (leer el sistema de jobs entero, escribir la cola con sus tests, cablearla).
- El caso mínimo que lo reproduce:
  ```kotlin
  services.addJobProcessor()          // sin addQueue previo
  app.start()                          // error: There are no registered queues
  ```

## MessageQueue.poll() tiene que bloquear, y eso no está escrito en ningún lado

- Slice: S2
- Módulo: trantor-core/queues
- Qué intentaba hacer: implementar `MessageQueue.poll(): List<ReceivedMessage>` para la cola del lab.
- Qué esperaba que pasara: que la interfaz dijera si `poll` es blocking o non-blocking. Es la decisión más importante de toda la implementación y la firma no la insinúa.
- Qué pasó: el poller no duerme nunca entre vueltas:
  ```kotlin
  while (running && !Thread.currentThread().isInterrupted) {
      val messages = queue.poll()
      if (messages.isEmpty()) continue   // MessageQueueProcessor.runPoller
  ```
  Un `poll()` que devuelve vacío en el acto convierte ese `while` en un spin a full core. `SqsQueue` no lo sufre porque hereda el long-poll de SQS (`pollWaitTimeSeconds` default 20), pero eso es una propiedad del driver, no del contrato. Los `sleep` que sí hay están sólo en los `catch` de error.
- Cómo lo resolví, o si no lo resolví: `InMemoryMessageQueue.poll()` bloquea hasta 200 ms (`DelayQueue.poll(timeout)`) y recién ahí devuelve vacío. Un test lo fija: `poll espera en vez de devolver vacio en el acto`.
- Cuánto me costó: 10 minutos, todos de lectura. No me lo comí en runtime porque leí `MessageQueueProcessor` antes de escribir la cola; el que no lo lea se come el spin.
- El caso mínimo que lo reproduce: `MessageQueue` cuyo `poll()` hace `return emptyList()` + `addJobProcessor()` → un core al 100% con la cola vacía.

## El JobProcessor no tiene stats: el HttpServer sí, los jobs no

- Slice: S2
- Módulo: trantor-core/jobs
- Qué intentaba hacer: medir el fan-out, que es lo que pide el slice: cuántos jobs se generan y cuánto tarda.
- Qué esperaba que pasara: algo equivalente a `HttpServer.stats` (`HttpServerStats`, con requests, dispatches y el thread pool). El precedente existe adentro del mismo framework.
- Qué pasó: cero. No hay contadores en `JobProcessor`, ni en `MessageQueueProcessor`, ni en `DefaultJobDispatcher`. Lo único que queda del paso de un job son dos `logger.info`. (`TaskPool` de `trantor-taskpool` sí tiene `getMetrics()`, pero es otro subsistema.)
- Cómo lo resolví, o si no lo resolví: los contadores los lleva la cola del lab (`enqueued` / `delivered` / `deleted` / in-flight) y se exponen por `GET /metrics/fanout` con un puerto en el core (`FanoutStatsSource`) y un adaptador en `platform`. Si mañana la cola es SQS de verdad, la métrica se va con ella y hay que sacarla de CloudWatch.
- Cuánto me costó: 20 minutos (decidir de dónde salen los números y no meter la cola adentro del core).
- El caso mínimo que lo reproduce: `rg "stats|metrics" trantor-core/src/dev/botta/trantor/core/jobs` → 0 hits, contra `HttpServer.kt` que expone `val stats: HttpServerStats`.

## El JobProcessor loguea el job entero en INFO: un chunk son 100 UUIDs por línea

- Slice: S2
- Módulo: trantor-core/jobs
- Qué intentaba hacer: leer el log del fan-out con 1.000 seguidores.
- Qué esperaba que pasara: una línea por job con el tipo y el id del job.
- Qué pasó: `logger.info("Executing job $job")` y otra igual al terminar. El `toString()` que se usa es el de la data class, así que `WriteTimelineChunk` escupió sus 100 `UserId` **dos veces por job**: 20 líneas de miles de caracteres para un fan-out de 14 ms.
- Cómo lo resolví, o si no lo resolví: `override fun toString()` en el job, que imprime `followers=100` en vez de la lista. `Job` ya trae un `toString()` propio (`describe("id=...", "createdAt=...")`), pero una data class lo pisa sin avisar — el default útil se pierde justo en los jobs que llevan payload grande.
- Cuánto me costó: 5 minutos.
- El caso mínimo que lo reproduce: `data class WriteTimelineChunk(val followerIds: List<UserId>): Job()` + `addJobProcessor()` → dos líneas INFO con la lista completa por cada job.
