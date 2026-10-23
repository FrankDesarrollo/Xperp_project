# Se construye desde la raíz del repo (necesita contracts/): docker compose build frontend
FROM node:22-alpine AS build
WORKDIR /workspace
COPY contracts ./contracts
COPY frontend/package.json frontend/package-lock.json ./frontend/
WORKDIR /workspace/frontend
RUN npm ci
COPY frontend ./
ARG PUBLIC_API_URL=http://localhost:8080/api/v1
ENV PUBLIC_API_URL=${PUBLIC_API_URL}
RUN npm run gen:api && npm run build

FROM nginx:1.29-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/build /usr/share/nginx/html
EXPOSE 80
