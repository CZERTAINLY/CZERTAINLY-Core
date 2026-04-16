#!/usr/bin/env bash
# upload-certificate-chain.sh
#
# Generates a 5-certificate chain locally with openssl, then uploads all
# certificates to the ILM platform.
#
# Chain structure:
#   Root CA  (self-signed)
#     └── Sub-CA 1
#           └── Sub-CA 2
#                 └── Sub-CA 3
#                       └── Leaf (end-entity)
#
# ILM automatically resolves the CA hierarchy chain on upload by matching
# issuer/subject DNs and verifying signatures — no explicit linking needed.
#
# Requires: openssl, curl, jq, base64

set -euo pipefail

# --- Defaults -----------------------------------------------------------------
ILM_HOST="http://localhost:8200"
CLIENT_CERT_PEM=""
KEEP_FILES=false
OUT_DIR=""

# --- Result variables ---------------------------------------------------------
CLIENT_CERT_HEADER_VAL=""
WORK_DIR=""

# Certificate UUIDs (populated during upload)
declare -a CERT_UUIDS=()

# Certificate roles, CNs and file base names (order: root → leaf)
CERT_ROLES=("Root CA" "Sub-CA 1" "Sub-CA 2" "Sub-CA 3" "Leaf")
CERT_CNS=("ILM Root CA" "ILM Sub-CA 1" "ILM Sub-CA 2" "ILM Sub-CA 3" "ILM Leaf")
CERT_FILES=("root" "sub-ca-1" "sub-ca-2" "sub-ca-3" "leaf")

# --- Usage --------------------------------------------------------------------
usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Required:
  --client-cert-pem FILE     ILM admin client certificate PEM

Optional:
  --ilm-host URL             URL of ILM API              (default: http://localhost:8200)
  --keep-files               Do not delete generated keys/certs on exit
  --out-dir DIR              Write generated PEM files to DIR instead of a temp dir

Requires: openssl, curl, jq, base64
EOF
  exit 1
}

# --- Helpers ------------------------------------------------------------------

log() { echo "==> $*" >&2; }
ok()  { echo "    OK: $*" >&2; }
die() { echo "ERROR: $*" >&2; exit 1; }

# ilm_curl METHOD PATH [extra curl args...]
# Fails with a clear message on non-2xx HTTP status.
ilm_curl() {
  local method="$1"; shift
  local path="$1"; shift
  local tmp http_code response
  tmp=$(mktemp)
  http_code=$(curl -s -o "$tmp" -w "%{http_code}" -X "$method" \
    -H "ssl-client-cert: ${CLIENT_CERT_HEADER_VAL}" \
    -H "content-type: application/json" \
    "${ILM_HOST}/api${path}" \
    "$@")
  response=$(<"$tmp"); rm -f "$tmp"
  if [[ "$http_code" -lt 200 || "$http_code" -ge 300 ]]; then
    die "HTTP ${http_code} on ${method} /api${path}: ${response}"
  fi
  echo "$response"
}

# require_uuid RESPONSE CONTEXT -- extracts .uuid from JSON; exits if missing or null
require_uuid() {
  local uuid
  uuid=$(echo "$1" | jq -r '.uuid // empty')
  [[ -z "$uuid" ]] && die "No UUID returned for $2. Response: $1"
  echo "$uuid"
}

# --- Argument parsing ---------------------------------------------------------
parse_args() {
  while [[ $# -gt 0 ]]; do
    case $1 in
      --client-cert-pem) CLIENT_CERT_PEM="$2"; shift 2 ;;
      --ilm-host)        ILM_HOST="$2";        shift 2 ;;
      --keep-files)      KEEP_FILES=true;      shift   ;;
      --out-dir)         OUT_DIR="$2";         shift 2 ;;
      --help|-h)         usage ;;
      *) echo "Unknown option: $1"; usage ;;
    esac
  done
}

# --- Validation ---------------------------------------------------------------
validate() {
  local errors=0

  [[ -z "$CLIENT_CERT_PEM" ]] && { echo "ERROR: --client-cert-pem is required"; errors=$((errors+1)); }
  [[ $errors -gt 0 ]] && usage

  [[ ! -f "$CLIENT_CERT_PEM" ]] && die "Client cert PEM not found: $CLIENT_CERT_PEM"

  command -v openssl &>/dev/null || die "openssl is required but not installed"
  command -v curl    &>/dev/null || die "curl is required but not installed"
  command -v jq      &>/dev/null || die "jq is required but not installed"
  command -v base64  &>/dev/null || die "base64 is required but not installed"

  # Build the ssl-client-cert header value: extract only the base64 body between
  # BEGIN/END CERTIFICATE markers, then URL-encode (+, / and = only).
  local _cert_b64
  _cert_b64=$(sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/p' "$CLIENT_CERT_PEM" \
    | grep -v "^-----" | tr -d '\n\r')
  CLIENT_CERT_HEADER_VAL=$(printf '%s' "$_cert_b64" | sed 's/+/%2B/g; s|/|%2F|g; s/=/%3D/g')
}

# --- Work directory setup -----------------------------------------------------
setup_work_dir() {
  if [[ -n "$OUT_DIR" ]]; then
    mkdir -p "$OUT_DIR"
    WORK_DIR="$OUT_DIR"
    log "Using output directory: ${WORK_DIR}"
  else
    WORK_DIR=$(mktemp -d)
    log "Created temp directory: ${WORK_DIR}"
    if [[ "$KEEP_FILES" == false ]]; then
      trap 'rm -rf "${WORK_DIR}"' EXIT
    fi
  fi

  if [[ "$KEEP_FILES" == true || -n "$OUT_DIR" ]]; then
    log "Generated files will be kept in: ${WORK_DIR}"
  fi
}

# --- Phase 1: Generate certificate chain locally ------------------------------

# write_openssl_ca_ext FILE — write CA extensions config to a file
write_openssl_ca_ext() {
  cat > "$1" <<'EXTEOF'
[v3_ca]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical, CA:true
keyUsage = critical, digitalSignature, cRLSign, keyCertSign
EXTEOF
}

# write_openssl_leaf_ext FILE — write leaf (end-entity) extensions config to a file
write_openssl_leaf_ext() {
  cat > "$1" <<'EXTEOF'
[v3_leaf]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical, CA:false
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
EXTEOF
}

generate_chain() {
  local ca_ext_file="${WORK_DIR}/ca.ext"
  local leaf_ext_file="${WORK_DIR}/leaf.ext"
  write_openssl_ca_ext  "$ca_ext_file"
  write_openssl_leaf_ext "$leaf_ext_file"

  # --- Root CA (self-signed) ---
  # OpenSSL 3.x does not accept -extfile/-extensions on 'req -x509'; use -addext instead.
  log "Generating Root CA key and self-signed certificate..."
  openssl genrsa -out "${WORK_DIR}/root.key" 2048 2>/dev/null
  openssl req -x509 -new -nodes \
    -key "${WORK_DIR}/root.key" \
    -days 3650 \
    -out "${WORK_DIR}/root.crt" \
    -subj "/CN=ILM Root CA/O=ILM/C=US" \
    -addext "subjectKeyIdentifier=hash" \
    -addext "basicConstraints=critical,CA:true" \
    -addext "keyUsage=critical,digitalSignature,cRLSign,keyCertSign" 2>/dev/null
  ok "Root CA: $(openssl x509 -noout -subject -in "${WORK_DIR}/root.crt" | sed 's/subject=//')"

  # --- Sub-CAs (signed by parent) ---
  local sub_parents=("root" "sub-ca-1" "sub-ca-2")
  local sub_names=("sub-ca-1" "sub-ca-2" "sub-ca-3")
  local sub_cns=("ILM Sub-CA 1" "ILM Sub-CA 2" "ILM Sub-CA 3")

  for i in "${!sub_names[@]}"; do
    local name="${sub_names[$i]}"
    local cn="${sub_cns[$i]}"
    local parent="${sub_parents[$i]}"
    log "Generating ${cn} key and certificate (signed by ${parent})..."
    openssl genrsa -out "${WORK_DIR}/${name}.key" 2048 2>/dev/null
    openssl req -new -nodes \
      -key "${WORK_DIR}/${name}.key" \
      -out "${WORK_DIR}/${name}.csr" \
      -subj "/CN=${cn}/O=ILM/C=US" 2>/dev/null
    openssl x509 -req \
      -in "${WORK_DIR}/${name}.csr" \
      -CA "${WORK_DIR}/${parent}.crt" \
      -CAkey "${WORK_DIR}/${parent}.key" \
      -CAcreateserial \
      -days 1825 \
      -out "${WORK_DIR}/${name}.crt" \
      -extensions v3_ca \
      -extfile "$ca_ext_file" 2>/dev/null
    ok "${cn}: $(openssl x509 -noout -subject -in "${WORK_DIR}/${name}.crt" | sed 's/subject=//')"
  done

  # --- Leaf certificate (signed by Sub-CA 3) ---
  log "Generating Leaf key and certificate (signed by Sub-CA 3)..."
  openssl genrsa -out "${WORK_DIR}/leaf.key" 2048 2>/dev/null
  openssl req -new -nodes \
    -key "${WORK_DIR}/leaf.key" \
    -out "${WORK_DIR}/leaf.csr" \
    -subj "/CN=ILM Leaf/O=ILM/C=US" 2>/dev/null
  openssl x509 -req \
    -in "${WORK_DIR}/leaf.csr" \
    -CA "${WORK_DIR}/sub-ca-3.crt" \
    -CAkey "${WORK_DIR}/sub-ca-3.key" \
    -CAcreateserial \
    -days 365 \
    -out "${WORK_DIR}/leaf.crt" \
    -extensions v3_leaf \
    -extfile "$leaf_ext_file" 2>/dev/null
  ok "Leaf: $(openssl x509 -noout -subject -in "${WORK_DIR}/leaf.crt" | sed 's/subject=//')"

  log "Certificate chain generated successfully."
}

# --- Phase 2: Upload certificates to ILM -------------------------------------
upload_certificates() {
  log "Uploading certificates to ILM (${ILM_HOST})..."

  for i in "${!CERT_FILES[@]}"; do
    local name="${CERT_FILES[$i]}"
    local role="${CERT_ROLES[$i]}"
    local crt_file="${WORK_DIR}/${name}.crt"
    local cert_b64 _resp uuid

    cert_b64=$(openssl x509 -in "$crt_file" -outform DER 2>/dev/null | base64 | tr -d '\n')

    log "Uploading ${role} (${CERT_CNS[$i]})..."
    _resp=$(ilm_curl POST /v1/certificates/upload -d \
      "$(jq -n --arg cert "$cert_b64" '{"certificate": $cert, "customAttributes": []}')")
    uuid=$(require_uuid "$_resp" "${role} upload")
    CERT_UUIDS+=("$uuid")
    ok "${role}  UUID: ${uuid}"
  done
}

# --- Summary ------------------------------------------------------------------
print_summary() {
  echo ""
  echo "===== Certificate Chain Upload Summary ====="
  printf "%-4s  %-10s  %-20s  %s\n" "IDX" "ROLE" "CN" "UUID"
  printf "%-4s  %-10s  %-20s  %s\n" "---" "----------" "--------------------" "------------------------------------"
  for i in "${!CERT_FILES[@]}"; do
    printf "%-4s  %-10s  %-20s  %s\n" \
      "$((i+1))" \
      "${CERT_ROLES[$i]}" \
      "${CERT_CNS[$i]}" \
      "${CERT_UUIDS[$i]:-N/A}"
  done
  echo ""
  if [[ -n "$OUT_DIR" || "$KEEP_FILES" == true ]]; then
    echo "Generated files retained in: ${WORK_DIR}"
  fi
}

# --- Main ---------------------------------------------------------------------
main() {
  parse_args "$@"
  validate
  setup_work_dir

  generate_chain
  upload_certificates
  print_summary
}

main "$@"
