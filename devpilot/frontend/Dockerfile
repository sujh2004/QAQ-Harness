# Frontend image: build with Node, serve the static bundle with nginx.
#
# VITE_API_BASE_URL is baked in at build time because Vite inlines it; compose passes it as a build
# argument so a deployment can point the bundle at a backend other than localhost.

FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
