FROM node:22-alpine AS build
WORKDIR /app

ARG VITE_API_URL=https://titlo10.fun:8080
ARG VITE_YANDEX_CLIENT_ID=
ARG VITE_REDIRECT_URI=
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_YANDEX_CLIENT_ID=$VITE_YANDEX_CLIENT_ID
ENV VITE_REDIRECT_URI=$VITE_REDIRECT_URI

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY deploy/nginx/messenger.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
