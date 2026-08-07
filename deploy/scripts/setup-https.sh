#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly NGINX_TEMPLATE="${SCRIPT_DIR}/../nginx/trekkey.conf.template"
readonly NGINX_TARGET="/etc/nginx/conf.d/trekkey.conf"
readonly BACKEND_HEALTH_URL="http://127.0.0.1:8080/api/organizations"

usage() {
  echo "Usage: sudo $0 <domain> <email>" >&2
  echo "Example: sudo $0 api.example.com admin@example.com" >&2
}

fail() {
  echo "setup-https: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

if [[ "$#" -ne 2 ]]; then
  usage
  exit 64
fi

if [[ "${EUID}" -ne 0 ]]; then
  fail "run this script with sudo"
fi

readonly DOMAIN="${1,,}"
readonly EMAIL="$2"

if [[ ! "${DOMAIN}" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ || "${DOMAIN}" != *.* ]]; then
  fail "invalid domain: ${DOMAIN}"
fi

if [[ ! "${EMAIL}" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
  fail "invalid email: ${EMAIL}"
fi

for command_name in certbot curl install nginx openssl sed systemctl; do
  require_command "${command_name}"
done

[[ -f "${NGINX_TEMPLATE}" ]] || fail "Nginx template not found: ${NGINX_TEMPLATE}"

curl \
  --fail \
  --silent \
  --show-error \
  --max-time 10 \
  "${BACKEND_HEALTH_URL}" >/dev/null \
  || fail "backend is not healthy at ${BACKEND_HEALTH_URL}"

rendered_config="$(mktemp)"
previous_config=""
nginx_was_active=false
nginx_was_enabled=false
nginx_started_by_script=false
config_replaced=false

if systemctl is-active --quiet nginx; then
  nginx_was_active=true
fi

if systemctl is-enabled --quiet nginx; then
  nginx_was_enabled=true
fi

cleanup() {
  local exit_code=$?

  rm -f "${rendered_config}"

  if [[ "${exit_code}" -ne 0 && "${config_replaced}" == true ]]; then
    if [[ -n "${previous_config}" ]]; then
      install -m 0644 "${previous_config}" "${NGINX_TARGET}"
    else
      rm -f "${NGINX_TARGET}"
    fi
  fi

  if [[ -n "${previous_config}" ]]; then
    rm -f "${previous_config}"
  fi

  if [[ "${exit_code}" -ne 0 ]]; then
    if [[ "${nginx_was_active}" == true ]]; then
      systemctl restart nginx >/dev/null 2>&1 || true
    elif [[ "${nginx_started_by_script}" == true ]]; then
      systemctl stop nginx >/dev/null 2>&1 || true
    fi

    if [[ "${nginx_was_enabled}" == false ]]; then
      systemctl disable nginx >/dev/null 2>&1 || true
    fi
  fi

  trap - EXIT
  exit "${exit_code}"
}
trap cleanup EXIT

sed "s/__DOMAIN__/${DOMAIN}/g" "${NGINX_TEMPLATE}" > "${rendered_config}"

readonly CERTIFICATE_PATH="/etc/letsencrypt/live/${DOMAIN}/fullchain.pem"
readonly PRIVATE_KEY_PATH="/etc/letsencrypt/live/${DOMAIN}/privkey.pem"

certificate_is_usable=false
if [[ -s "${CERTIFICATE_PATH}" \
    && -s "${PRIVATE_KEY_PATH}" ]] \
    && openssl x509 -checkend 604800 -noout -in "${CERTIFICATE_PATH}"; then
  certificate_is_usable=true
fi

if [[ "${certificate_is_usable}" == false ]]; then
  if [[ "${nginx_was_active}" == true ]]; then
    systemctl stop nginx
  fi

  certbot certonly \
    --standalone \
    --cert-name "${DOMAIN}" \
    --domain "${DOMAIN}" \
    --email "${EMAIL}" \
    --agree-tos \
    --non-interactive
else
  echo "Existing certificate found for ${DOMAIN}; issuance skipped."
fi

install -d -m 0755 "$(dirname -- "${NGINX_TARGET}")"
if [[ -f "${NGINX_TARGET}" ]]; then
  previous_config="$(mktemp)"
  install -m 0644 "${NGINX_TARGET}" "${previous_config}"
fi

install -m 0644 "${rendered_config}" "${NGINX_TARGET}"
config_replaced=true

nginx -t
systemctl enable nginx >/dev/null
systemctl restart nginx
nginx_started_by_script=true

curl \
  --fail \
  --silent \
  --show-error \
  --max-time 10 \
  --resolve "${DOMAIN}:443:127.0.0.1" \
  "https://${DOMAIN}/api/organizations" >/dev/null \
  || fail "HTTPS proxy health check failed"

config_replaced=false
echo "HTTPS setup completed for https://${DOMAIN}"
