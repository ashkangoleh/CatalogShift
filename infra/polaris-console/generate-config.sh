#!/bin/sh
# Adapted from Apache Polaris Tools — paths adjusted for nginx:alpine

# Generate runtime configuration from environment variables
cat > /usr/share/nginx/html/config.js << EOF
// Runtime configuration generated from environment variables
window.APP_CONFIG = {
  VITE_POLARIS_API_URL: '${VITE_POLARIS_API_URL}',
  VITE_POLARIS_REALM: '${VITE_POLARIS_REALM}',
  VITE_POLARIS_PRINCIPAL_SCOPE: '${VITE_POLARIS_PRINCIPAL_SCOPE}',
  VITE_OAUTH_TOKEN_URL: '${VITE_OAUTH_TOKEN_URL}',
  VITE_POLARIS_REALM_HEADER_NAME: '${VITE_POLARIS_REALM_HEADER_NAME}',
  VITE_OIDC_ISSUER_URL: '${VITE_OIDC_ISSUER_URL}',
  VITE_OIDC_CLIENT_ID: '${VITE_OIDC_CLIENT_ID}',
  VITE_OIDC_REDIRECT_URI: '${VITE_OIDC_REDIRECT_URI}',
  VITE_OIDC_SCOPE: '${VITE_OIDC_SCOPE}'
};
EOF

echo "Generated config.js with runtime configuration:"
cat /usr/share/nginx/html/config.js

# Start nginx
exec nginx -g 'daemon off;'
