# Build the SPA, then serve the static bundle with Caddy (SPA fallback + /api proxy).
FROM node:22-alpine AS build

ENV NPM_CONFIG_UPDATE_NOTIFIER=false
ENV NPM_CONFIG_FUND=false

WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . ./
RUN npm run build

FROM caddy:2-alpine
WORKDIR /app
COPY Caddyfile ./
COPY --from=build /app/dist ./dist

CMD ["caddy", "run", "--config", "Caddyfile", "--adapter", "caddyfile"]
