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
- Cómo lo resolví, o si no lo resolví: Gradle JVM y Project SDK a `corretto-25` (el mismo que Crafty). `.idea/` está en gitignore: hay que setearlo en cada máquina.
- Cuánto me costó: 5 minutos
- El caso mínimo que lo reproduce: abrir el proyecto en IntelliJ con Gradle JVM = 17; sync.

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
  `Location.CLASSPATH` + `resources/public` es lo que hace que `installDist` siga sirviendo `/` y `/modelo.html`.
- Cuánto me costó: 14 minutos (confirmar que no hay staticFiles, leer `HttpServer` / `HttpServerSettings`, dar con `ServiceRegistry.configure` para llegar antes de la construcción del server).
- El caso mínimo que lo reproduce: `rg staticFiles ~/Documents/projects/404/trantor/trantor-web` → 0 hits. `HttpServer.kt` línea del `settings.configureJavalin(javalinConfig)`. Sin ese seteo, `GET /` es 404.
