import type { ReactNode } from 'react';

export function PortadaPage() {
  return (
    <main className="page">
      <div className="lede">
        <h1>Fan-out híbrido para entrevistas</h1>
        <p className="question">¿Cómo diseñarías el timeline de Twitter?</p>
        <p>
          Este laboratorio no es un clon. Existe para responder esa pregunta con un número adelante, y para
          someter a Trantor a un proyecto que no es Crafty. Hoy están construidos el modelo y el fan-out on
          write. El híbrido y la hidratación vienen después; no hay botones de lo que todavía no existe.
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
              Todavía no lo medimos en este lab: S3 lo va a fijar como constante con la unidad en el nombre,
              y los tests van a ser 49 fans sí / 51 no. El número no sale de intuición de producto; sale de
              cuánto cuesta una escritura de timeline contra una lectura mergeada. Hasta no tener ese
              número, no lo invento en la entrevista: describo <em>cómo lo elegiría</em>.
            </p>
          </Step>
          <Step n={5} title="Qué se sacrifica: consistencia distinta por camino">
            <p className="answer__punch">
              «El feed de los seguidores puede ir atrasado respecto del post. El autor tiene que ver lo
              suyo ya. Son <strong>dos caminos de lectura</strong>, no un bug: el precomputado es
              eventualmente consistente; el del autor no espera al fan-out.»
            </p>
            <p className="deep">
              En este lab eso se va a apoyar en hidratar IDs desde cache y en <code>defer</code> (S4). El
              timeline ya se escribe solo al publicar; lo que devuelve siguen siendo IDs, y el cálculo de
              por qué IDs y no el post entero está en <a href="/modelo.html">modelo</a>.
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
        </div>
        <p className="run__note" style={{ textAlign: 'left', marginTop: 12 }}>
          Híbrido, lectura, outbox e infra están en la nav, apagados, hasta el slice que los construya.
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
