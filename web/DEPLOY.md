# Messenger Web Deployment

## 1) Build variables for Docker
Create `.env` (for docker compose variables):

```bash
cat > .env <<EOF
VITE_YANDEX_CLIENT_ID=your_yandex_client_id
VITE_REDIRECT_URI=https://chat.example.com/auth/callback
EOF
```

## 2) Optional local production env file
Create `.env.production` from example (optional, for non-docker build):

```bash
cp .env.production.example .env.production
```

Set values:
- `VITE_API_URL=https://titlo10.fun:8080`
- `VITE_YANDEX_CLIENT_ID=<your oauth id>`
- `VITE_REDIRECT_URI=https://<your-frontend-domain>/auth/callback`

## 3) Build and run (Docker)

```bash
docker compose up -d --build
```

App will be available on `http://<server-ip>:8081`.

## 4) Public domain + HTTPS
Put reverse proxy (Nginx/Caddy/Traefik) in front of `8081` and attach SSL cert.

Minimal Nginx vhost example:

```nginx
server {
    listen 80;
    server_name chat.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name chat.example.com;

    ssl_certificate /etc/letsencrypt/live/chat.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/chat.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 5) What backend team must enable
1. CORS allowlist for frontend domain:
   - `https://<your-frontend-domain>`
   - optional dev origin: `http://localhost:3000`
2. `OPTIONS` preflight support on API routes.
3. OAuth callback in provider settings:
   - `https://<your-frontend-domain>/auth/callback`
4. If cookies are used for auth:
   - `Secure`, `HttpOnly`, `SameSite=None`.

## 6) Smoke check after deploy
1. Open `https://<your-frontend-domain>/login`
2. Complete OAuth
3. In browser devtools verify callback request goes to:
   - `https://titlo10.fun:8080/api/auth/yandex/callback`
