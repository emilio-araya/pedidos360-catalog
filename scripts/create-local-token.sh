#!/usr/bin/env bash
set -euo pipefail

ISSUER="${LOCAL_JWT_ISSUER:-https://login.microsoftonline.com/pedidos360-local/v2.0}"
AUDIENCE="${LOCAL_JWT_AUDIENCE:-api://150f51db-4084-4979-b1a1-e6a6e7893a01}"
SECRET="${LOCAL_JWT_HMAC_SECRET:-pedidos360-local-secret-change-me-32-bytes-minimum}"
ROLES="${1:-Cliente}"

if (( ${#SECRET} < 32 )); then
  echo "LOCAL_JWT_HMAC_SECRET debe tener al menos 32 caracteres" >&2
  exit 1
fi

roles_json="["
separator=""
IFS=',' read -r -a requested_roles <<< "$ROLES"
for role in "${requested_roles[@]}"; do
  case "$role" in
    Admin|Operador|Cliente) ;;
    *)
      echo "Rol inválido: $role (use Admin, Operador o Cliente)" >&2
      exit 1
      ;;
  esac
  roles_json+="${separator}\"${role}\""
  separator=","
done
roles_json+="]"

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

issued_at="$(date +%s)"
expires_at="$((issued_at + 3600))"
header="$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | base64url)"
payload="$(printf '{"iss":"%s","sub":"local-user","aud":"%s","iat":%s,"nbf":%s,"exp":%s,"roles":%s}' \
  "$ISSUER" "$AUDIENCE" "$issued_at" "$issued_at" "$expires_at" "$roles_json" | base64url)"
signature="$(printf '%s' "${header}.${payload}" \
  | openssl dgst -sha256 -hmac "$SECRET" -binary \
  | base64url)"

printf '%s.%s.%s\n' "$header" "$payload" "$signature"
