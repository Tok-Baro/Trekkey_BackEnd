# Exact Node release; deployment must additionally record the resolved image digest.
FROM node:22.23.2-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY tsconfig.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev --ignore-scripts --no-audit --no-fund

FROM node:22.23.2-bookworm-slim AS runtime
RUN groupadd --gid 10001 trekkey && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin trekkey
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build --chown=10001:10001 /app/package.json ./
COPY --from=build --chown=10001:10001 /app/node_modules ./node_modules
COPY --from=build --chown=10001:10001 /app/dist ./dist
USER 10001:10001
ENTRYPOINT ["node", "dist/src/server-launch.js"]
