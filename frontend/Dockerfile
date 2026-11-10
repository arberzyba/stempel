# syntax=docker/dockerfile:1

# ---- Stage 1: build the React app with Node ----
FROM node:24-alpine AS build
WORKDIR /app

# Install dependencies first (cached as long as package*.json don't change).
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- Stage 2: serve the built files with nginx (no Node, no source code) ----
# The "unprivileged" variant runs nginx as a normal user on port 8080.
FROM nginxinc/nginx-unprivileged:1.30-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
