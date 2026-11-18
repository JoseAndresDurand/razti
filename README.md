# RAZTI

Arena multijugador 2D en tiempo real para 4 jugadores, ambientada en el campus de Ingenierías. Cada jugador coloca bombas para abrirse paso y eliminar a los demás; gana el último en pie.

Proyecto académico de programación funcional: las reglas del juego son funciones puras en Clojure/ClojureScript (`.cljc`) que comparten el servidor (JVM) y el navegador.

## Controles

| Tecla | Acción |
| :--- | :--- |
| W A S D o flechas | Moverse |
| Espacio | Colocar una bomba |

Para jugar: escribe tu nombre (1 a 12 letras, números, `_` o `-`, sin tildes ni espacios), crea una sala y comparte el código de 5 caracteres. Los demás se unen con ese código. El creador inicia la partida cuando hay 2 o más jugadores.

## Jugar en local

### Camino 1: solo Docker

```bash
docker build -t razti .
docker run --rm -p 127.0.0.1:3000:3000 razti
```

Abre <http://127.0.0.1:3000>. El contenedor corre como usuario sin privilegios y solo se publica en tu equipo (`127.0.0.1`).

### Camino 2: toolchain

Requisitos: JDK 21, [Clojure CLI](https://clojure.org/guides/install_clojure) 1.12, Node 22 y pnpm (`corepack enable`).

```bash
pnpm install --frozen-lockfile --ignore-scripts
pnpm exec shadow-cljs release app
clojure -M:run
```

Abre <http://127.0.0.1:3000>.

Para probar con varios jugadores en el mismo equipo, cada uno necesita su propia sesión: usa ventanas normales y de incógnito, `127.0.0.1` y `localhost`, o navegadores distintos. Las pestañas de una misma ventana comparten la sesión.

### En tu red local

Por defecto el servidor solo escucha en `127.0.0.1`. Para que otros equipos de tu red se unan, arranca con la IP de tu equipo y permite ese origen:

```bash
RAZTI_HOST=192.168.1.20 RAZTI_ALLOWED_ORIGINS=http://192.168.1.20:3000 clojure -M:run
```

Los demás abren `http://192.168.1.20:3000`. Hazlo solo en redes de confianza y detén el servidor al terminar.

## Jugar por Internet (Codespaces + Cloudflare Quick Tunnel)

Sin servidor propio ni costo fijo:

1. En GitHub, **Code → Codespaces → New codespace**. El devcontainer instala el toolchain.
2. En la terminal del Codespace, expón el puerto 3000 con un [Cloudflare Quick Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/do-more-with-tunnels/trycloudflare/):
   ```bash
   curl -fsSL -o /tmp/cloudflared https://github.com/cloudflare/cloudflared/releases/download/2026.10.7/cloudflared-linux-amd64
   chmod +x /tmp/cloudflared
   /tmp/cloudflared tunnel --url http://127.0.0.1:3000
   ```
   Copia la URL `https://<palabras>.trycloudflare.com` que imprime.
3. En otra terminal, arranca el juego permitiendo ese origen:
   ```bash
   pnpm exec shadow-cljs release app
   RAZTI_ALLOWED_ORIGINS=https://<palabras>.trycloudflare.com clojure -M:run
   ```
4. Comparte la URL del túnel. La barra inferior debe decir «Conexión: WebSocket».
5. Al terminar, detén el túnel y el servidor (Ctrl+C) y el Codespace.

No uses la URL de **Ports** de Codespaces para jugar: ese proxy reescribe el origen de las peticiones y rompe el WebSocket.

## Desarrollo

```bash
clojure -M:dev                        # servidor en http://127.0.0.1:3000
pnpm exec shadow-cljs watch app       # cliente con recarga en caliente
```

Con `clojure -M:dev`, la tecla **G** muestra la grilla lógica sobre el mapa.

Verificaciones (las mismas de la CI):

```bash
clojure -M:test                       # pruebas en la JVM (motor, servidor y 4 bots)
pnpm exec shadow-cljs compile test && node target/test/node-tests.js   # pruebas en Node
clj-kondo --lint src test
clojure -M:cljfmt check
pnpm exec shadow-cljs release app
clojure -T:build uber
clojure -M:smoke http://127.0.0.1:3000   # humo contra un servidor en marcha
```

## Licencias

- Código: [MIT](LICENSE).
- Imágenes de `resources/public/assets/` (mapa y personaje): [CC BY 4.0](resources/public/assets/LICENSE).
