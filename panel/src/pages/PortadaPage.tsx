import type { ReactNode } from 'react';

export function PortadaPage() {
  return (
    <main className="page">
      <div className="lede">
        <h1>Fan-out híbrido para entrevistas</h1>
        <p className="question">¿Cómo diseñarías el timeline de Twitter?</p>
        <p>
          Este laboratorio no es un clon. Existe para responder esa pregunta con un número adelante, y para
          someter a Trantor a un proyecto que no es Crafty. Hoy están construidos el modelo, el fan-out on
          write y el híbrido con su umbral. La hidratación y el outbox vienen después; no hay botones de lo
          que todavía no existe.
        </p>
      </div>

      <section className="section" id="respuesta">
        <h2>Respuesta de entrevista, en pasos</h2>
        <div className="answer">
          <p className="answer__q">«¿Cómo diseñarías el timeline de Twitter?»</p>
          <Step n={1} title="Lo primero: separar los dos problemas">
            <p>
              «El timeline parece un problema de lectura y no lo es. Hay <strong>dos costos</strong>, y
              elegís en cuál pagar: o multiplicás la <em>escritura</em> cuando alguien publica, o
              multiplicás la <em>lectura</em> cada vez que alguien abre el feed. Decirlo primero ordena
              toda la respuesta.»
            </p>
            <p className="deep">
              Si no separás, terminás discutiendo cache, NoSQL o Kafka sin haber elegido dónde vive la
              amplificación. El resto de la respuesta es una consecuencia de esa elección.
            </p>
          </Step>
          <Step n={2} title="Fan-out on write: barato de leer, caro de escribir">
            <p>
              «Cuando publicás, copio el post —en este lab, el <strong>ID</strong>— al timeline
              precomputado de cada seguidor. Leer es una lista ya armada. Escribir no: un post de alguien
              con 50 mil seguidores son <strong>50 mil escrituras</strong>.»
            </p>
            <p className="deep">
              Eso es el problema de la celebridad. No es que 50 mil filas no entren en una base: es que esa
              publicación tarda, satura workers y le pega a gente que no está leyendo ahora. El feed de un
              usuario normal, en cambio, se lee en O(ventana).
            </p>
          </Step>
          <Step n={3} title="Fan-out on read: barato de escribir, caro de leer">
            <p>
              «La inversa: guardo el post una vez, en el autor. Al leer, miro a quién seguís y mergeo sus
              posts recientes. Publicar es barato. Abrir el home lo paga <strong>cada lectura</strong>, y
              escala con cuánta gente seguís, no con cuántos te siguen a vos.»
            </p>
            <p className="deep">
              Sirve para celebridades: no vas a escribir 80 millones de timelines cuando publica. No sirve
              como default para todo el mundo: el home se vuelve un join pesado en el camino caliente.
            </p>
          </Step>
          <Step n={4} title="El híbrido, y de dónde sale el umbral">
            <p>
              «Para el usuario normal, fan-out on write. Para quien cruza un{' '}
              <strong>umbral de seguidores</strong>, no: se lo trata como celebridad y al leer se mergea el
              timeline precomputado con los posts recientes de esas cuentas. El umbral es el punto donde{' '}
              <em>N escrituras por post</em> empiezan a salir más caras que mergear esa cuenta en cada
              lectura.»
            </p>
            <p className="deep">
              Ya está construido: el umbral son 10.000 seguidores, arriba no se despacha nada y al leer se
              mergea. Por qué ahí: en el umbral un post cuesta 101 jobs y ~140 ms de propagación, y un
              millón de seguidores serían 10.001 jobs de un solo post; para el otro lado, cada celebridad
              que seguís agrega una consulta a todas tus lecturas, así que un umbral bajo devuelve el
              problema al camino caliente. Está en <a href="/hibrido.html">el híbrido</a>.
            </p>
          </Step>
          <Step n={5} title="Qué se sacrifica: consistencia distinta por camino">
            <p className="answer__punch">
              «El feed de los seguidores puede ir atrasado respecto del post. El autor tiene que ver lo
              suyo ya. Son <strong>dos caminos de lectura</strong>, no un bug: el precomputado es
              eventualmente consistente; el del autor no espera al fan-out.»
            </p>
            <p className="deep">
              En este lab eso vive en hidratar IDs desde cache y en <code>defer</code>:{' '}
              <a href="/lectura.html">lectura</a>. El timeline se escribe solo al publicar; el autor
              hidrata lo suyo sin esperar. El cálculo de por qué IDs y no el post entero está en{' '}
              <a href="/modelo.html">modelo</a>.
            </p>
          </Step>
        </div>
      </section>

      <section className="section">
        <h2>Qué hay construido</h2>
        <div className="index">
          <a className="index__item" href="/modelo.html">
            <span className="index__id">S1</span>
            <span className="index__body">
              <h3>El modelo</h3>
              <p className="index__q">
                Posts, follows y un timeline de IDs. Calculadora en vivo: 50 millones de timelines son 640
                GB de UUID contra 12,8 TB de post completo.
              </p>
              <span className="index__contrast">
                <span>
                  el timeline guarda <b>IDs</b>, no posts
                </span>
                <span>
                  <b className="n">20×</b> menos disco
                </span>
              </span>
            </span>
            <span className="index__go" aria-hidden="true">
              →
            </span>
          </a>

          <a className="index__item" href="/fanout.html">
            <span className="index__id">S2</span>
            <span className="index__body">
              <h3>Fan-out on write</h3>
              <p className="index__q">
                Publicar despacha un job y contesta 201. La cola escribe los timelines de los seguidores,
                de a 100 por job.
              </p>
              <span className="index__contrast">
                <span>
                  1.000 seguidores = <b>11 jobs</b>
                </span>
                <span>
                  fan-out completo en <b className="n">14 ms</b>
                </span>
              </span>
            </span>
            <span className="index__go" aria-hidden="true">
              →
            </span>
          </a>

          <a className="index__item" href="/hibrido.html">
            <span className="index__id">S3</span>
            <span className="index__body">
              <h3>El híbrido</h3>
              <p className="index__q">
                Por encima del umbral no hay fan-out: el post se entrega al leer, mergeado con el timeline
                precomputado.
              </p>
              <span className="index__contrast">
                <span>
                  umbral: <b>10.000</b> seguidores
                </span>
                <span>
                  arriba, <b className="n">0</b> escrituras al publicar
                </span>
              </span>
            </span>
            <span className="index__go" aria-hidden="true">
              →
            </span>
          </a>

          <a className="index__item" href="/lectura.html">
            <span className="index__id">S4</span>
            <span className="index__body">
              <h3>Lectura</h3>
              <p className="index__q">
                El feed hidrata el texto desde cache. El autor ve su post al toque; el seguidor espera
                al fan-out.
              </p>
              <span className="index__contrast">
                <span>
                  dos caminos, <b>dos consistencias</b>
                </span>
                <span>
                  autor en <b className="n">t=0</b>
                </span>
              </span>
            </span>
            <span className="index__go" aria-hidden="true">
              →
            </span>
          </a>
        </div>
        <p className="run__note" style={{ textAlign: 'left', marginTop: 12 }}>
          Outbox e infra están en la nav, apagados, hasta el slice que los construya.
        </p>
      </section>

      <p className="footnote">
        El panel se escribe en <code>panel/</code> (React + Vite) y Vite lo escupe a{' '}
        <code>resources/public</code>. Trantor lo sirve con <code>HttpServerSettings.configureJavalin</code>
        : trantor-web no tiene estáticos. Ver <code>FRICCION.md</code>.
      </p>
    </main>
  );
}

function Step({ n, title, children }: { n: number; title: string; children: ReactNode }) {
  return (
    <div className="answer__step">
      <span className="answer__n">{n}</span>
      <div>
        <h3 className="answer__h">{title}</h3>
        {children}
      </div>
    </div>
  );
}
