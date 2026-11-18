# RAZTI · imagen multietapa (brief C29; TDD M2/S02 §4)
# Etapa 1: compila el cliente (shadow-cljs release) y el uberjar.
# Etapa 2: JRE 21 con usuario sin privilegios.

FROM node:22.23.5-bookworm-slim AS node

FROM clojure:temurin-21-tools-deps-1.12.6.1673-bookworm-slim AS build
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s ../lib/node_modules/corepack/dist/corepack.js /usr/local/bin/corepack \
 && corepack enable

WORKDIR /src
COPY deps.edn build.clj ./
RUN clojure -P -M:run && clojure -P -T:build

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --ignore-scripts

COPY shadow-cljs.edn ./
COPY src ./src
COPY resources ./resources
RUN pnpm exec shadow-cljs release app \
 && clojure -T:build uber

FROM eclipse-temurin:21.0.13_9-jre-noble
RUN groupadd --system --gid 10001 razti \
 && useradd --system --uid 10001 --gid razti --no-create-home --shell /usr/sbin/nologin razti
WORKDIR /app
COPY --from=build --chown=root:root /src/target/razti.jar /app/razti.jar
USER 10001:10001

# Solo dentro del contenedor el servidor escucha en todas las interfaces (C3);
# el host publica únicamente 127.0.0.1:3000 (docker run -p 127.0.0.1:3000:3000).
ENV RAZTI_HOST=0.0.0.0 \
    RAZTI_PORT=3000
EXPOSE 3000
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms256m", "-Xmx256m", "-jar", "/app/razti.jar"]
