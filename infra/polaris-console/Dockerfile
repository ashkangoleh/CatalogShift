# Build stage — public equivalent of ubi9/nodejs-22-minimal
FROM node:22-alpine AS builder

ENV VITE_POLARIS_API_URL=http://polaris:8181
ENV VITE_POLARIS_REALM=POLARIS
ENV VITE_POLARIS_PRINCIPAL_SCOPE=PRINCIPAL_ROLE:ALL
ENV VITE_OAUTH_TOKEN_URL=http://polaris:8181/api/catalog/v1/oauth/tokens
ENV VITE_POLARIS_REALM_HEADER_NAME=Polaris-Realm

WORKDIR /app

COPY third_party/polaris-tools/console/package.json ./
RUN npm install --legacy-peer-deps

COPY third_party/polaris-tools/console/ .
RUN npm run build

# Production stage — public equivalent of ubi9/nginx-126
FROM nginx:1.27-alpine

# Create polaris user matching the upstream UID/GID convention
RUN addgroup -g 10001 polaris && \
    adduser -u 10000 -G polaris -D polaris

# Copy LICENSE-BUNDLE and NOTICE files
COPY third_party/polaris-tools/console/LICENSE-BUNDLE /LICENSE
COPY third_party/polaris-tools/console/NOTICE /NOTICE

# Remove default nginx server (listens on :80), then add ours
RUN rm -f /etc/nginx/conf.d/default.conf /etc/nginx/http.d/default.conf
COPY infra/polaris-console/nginx.conf /etc/nginx/conf.d/default.conf

# Copy runtime config generation script (adapted for Alpine paths)
COPY infra/polaris-console/generate-config.sh /generate-config.sh
RUN chmod +x /generate-config.sh

# Copy built application to Alpine nginx web root
COPY --from=builder /app/dist /usr/share/nginx/html

# Fix ownership for nginx
RUN chown -R polaris:polaris /usr/share/nginx/html \
    && chown -R polaris:polaris /var/log/nginx \
    && chown -R polaris:polaris /etc/nginx/conf.d \
    && chown -R polaris:polaris /var/cache/nginx \
    && touch /var/run/nginx.pid \
    && chown polaris:polaris /var/run/nginx.pid

USER polaris
EXPOSE 8080
ENTRYPOINT ["/generate-config.sh"]
