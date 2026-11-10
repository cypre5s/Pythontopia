FROM node:22.19-alpine@sha256:d2166de198f26e17e5a442f537754dd616ab069c47cc57b889310a717e0abbf9 AS build
WORKDIR /app/frontend
ARG VITE_TIME_ZONE=Asia/Shanghai
ENV VITE_TIME_ZONE=$VITE_TIME_ZONE
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
COPY shared/world/ /app/shared/world/
RUN npm run build
FROM node:22.19-alpine@sha256:d2166de198f26e17e5a442f537754dd616ab069c47cc57b889310a717e0abbf9 AS admin-build
WORKDIR /app/admin
ARG VITE_TIME_ZONE=Asia/Shanghai
ENV VITE_TIME_ZONE=$VITE_TIME_ZONE
COPY admin/package*.json ./
RUN npm ci
COPY admin/ ./
COPY shared/world/ /app/shared/world/
RUN npm run build
FROM nginx:1.28-alpine@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236
COPY --from=build /app/frontend/dist /usr/share/nginx/html
COPY --from=admin-build /app/admin/dist /usr/share/nginx/html/teacher
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
