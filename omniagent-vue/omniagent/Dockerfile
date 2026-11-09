FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
ARG VITE_AGENT_BASE=/agent
ENV VITE_AGENT_BASE=$VITE_AGENT_BASE
COPY . .
RUN npm run build-only

FROM nginx:1.27-alpine
LABEL org.opencontainers.image.source=https://github.com/Eny-O/omniagent-java
LABEL org.opencontainers.image.description="OmniAgent Vue frontend and reverse proxy"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
