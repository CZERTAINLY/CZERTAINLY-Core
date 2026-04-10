#!/usr/bin/env bash
# timestamping-setup.sh
#
# Automates the ILM timestamping environment setup:
#   1. Creates three connectors (credential-provider, EJBCA, crypto-provider)
#   2. Uploads the CA certificate and marks it trusted
#   3. Creates a SoftKeyStore credential from a PKCS12 bundle
#   4. Creates an EJBCA authority instance
#   5. Creates a soft token, token profile, and RSA 2048 key pair
#   6. Creates an RA profile (resolving EJBCA profile IDs dynamically)
#   7. Issues a TSA certificate with the requested DN
#   8. Creates and enables a TSP profile
#
# Requires: curl, jq, base64

set -euo pipefail

# ─── Defaults ─────────────────────────────────────────────────────────────────
ILM_HOST="http://localhost:8200"
CLIENT_CERT_PEM=""

CONNECTOR_HOST="localhost"
PORT_CRED_PROVIDER="8201"
PORT_EJBCA="8210"
PORT_CRYPTO_PROVIDER="8230"

PKCS12_BUNDLE=""
PKCS12_PASSWORD="00000000"
TOKEN_PASSWORD=""          # defaults to PKCS12_PASSWORD when empty
CA_PEM=""
CERTIFICATE_DN=""

EJBCA_URL="https://ejbca.3key.company/ejbca/ejbcaws/ejbcaws?wsdl"
EJBCA_EE_PROFILE="DemoTSAEndEntityProfile"
EJBCA_CERT_PROFILE="DemoTSAEECertificateProfile"
EJBCA_CA_NAME="DemoRootCA_2307RSA"

CREDENTIAL_NAME="ejbca.3key.company"
AUTHORITY_NAME="ejbca.3key.company"
TOKEN_NAME="tsa"
TOKEN_PROFILE_NAME="tsa"
KEY_NAME="tsa-rsa"
RA_PROFILE_NAME="tsa-non-qualified"
TSP_PROFILE_NAME="tsp-1"

# ─── Usage ────────────────────────────────────────────────────────────────────
usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Required:
  --client-cert-pem FILE      ILM admin client certificate PEM
  --pkcs12-bundle FILE        Path to PKCS12 bundle with EJBCA client credentials
  --ca-pem FILE               Path to CA certificate PEM to upload and mark trusted
  --certificate-dn DN         Common name for the TSA certificate (e.g. "my-tsa")

Connector options (defaults: localhost, ports 8201/8210/8230):
  --connector-host HOST       Hostname for connectors as seen from ILM server
  --port-cred-provider PORT   common-credential-provider port     (default: 8201)
  --port-ejbca PORT           ejbca-ng-connector port             (default: 8210)
  --port-crypto-provider PORT software-cryptography-provider port (default: 8230)

Credential/token options:
  --pkcs12-password PASS      PKCS12 bundle password     (default: 00000000)
  --token-password PASS       Soft token PIN             (default: same as pkcs12-password)

ILM API auth:
  --ilm-host HOST             URL of ILM API                (default: http://localhost:8200)
  --client-cert-pem FILE      Admin client certificate PEM  (required)

EJBCA options:
  --ejbca-url URL             EJBCA WSDL URL      (default https://ejbca.3key.company/ejbca/ejbcaws/ejbcaws?wsdl)
  --ejbca-ca NAME             Issuing CA name     (default: DemoRootCA_2307RSA)
  --ejbca-ee-profile NAME     End entity profile  (default: DemoTSAEndEntityProfile)
  --ejbca-cert-profile NAME   Certificate profile (default: DemoTSAEECertificateProfile)

Object names (usually no need to change):
  --credential-name NAME      (default: ejbca.3key.company)
  --authority-name NAME       (default: ejbca.3key.company)
  --token-name NAME           (default: tsa)
  --token-profile-name NAME   (default: tsa)
  --key-name NAME             (default: tsa-rsa)
  --ra-profile-name NAME      (default: tsa-non-qualified)
  --tsp-profile-name NAME     (default: tsp-1)
EOF
  exit 1
}

# ─── Parse arguments ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case $1 in
    --pkcs12-bundle)          PKCS12_BUNDLE="$2";          shift 2 ;;
    --pkcs12-password)        PKCS12_PASSWORD="$2";        shift 2 ;;
    --token-password)         TOKEN_PASSWORD="$2";         shift 2 ;;
    --ca-pem)                 CA_PEM="$2";                 shift 2 ;;
    --certificate-dn)         CERTIFICATE_DN="$2";         shift 2 ;;
    --ejbca-url)              EJBCA_URL="$2";              shift 2 ;;
    --connector-host)         CONNECTOR_HOST="$2";         shift 2 ;;
    --port-cred-provider)     PORT_CRED_PROVIDER="$2";     shift 2 ;;
    --port-ejbca)             PORT_EJBCA="$2";             shift 2 ;;
    --port-crypto-provider)   PORT_CRYPTO_PROVIDER="$2";   shift 2 ;;
    --ilm-host)               ILM_HOST="$2";               shift 2 ;;
    --client-cert-pem)        CLIENT_CERT_PEM="$2";        shift 2 ;;
    --ejbca-ee-profile)       EJBCA_EE_PROFILE="$2";       shift 2 ;;
    --ejbca-cert-profile)     EJBCA_CERT_PROFILE="$2";     shift 2 ;;
    --ejbca-ca)               EJBCA_CA_NAME="$2";          shift 2 ;;
    --credential-name)        CREDENTIAL_NAME="$2";        shift 2 ;;
    --authority-name)         AUTHORITY_NAME="$2";         shift 2 ;;
    --token-name)             TOKEN_NAME="$2";             shift 2 ;;
    --token-profile-name)     TOKEN_PROFILE_NAME="$2";     shift 2 ;;
    --key-name)               KEY_NAME="$2";               shift 2 ;;
    --ra-profile-name)        RA_PROFILE_NAME="$2";        shift 2 ;;
    --tsp-profile-name)       TSP_PROFILE_NAME="$2";       shift 2 ;;
    --help|-h)                usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

# ─── Validate ─────────────────────────────────────────────────────────────────
errors=0
[[ -z "$PKCS12_BUNDLE" ]]    && { echo "ERROR: --pkcs12-bundle is required";    errors=$((errors+1)); }
[[ -z "$CA_PEM" ]]           && { echo "ERROR: --ca-pem is required";           errors=$((errors+1)); }
[[ -z "$CERTIFICATE_DN" ]]   && { echo "ERROR: --certificate-dn is required";   errors=$((errors+1)); }
[[ -z "$CLIENT_CERT_PEM" ]]  && { echo "ERROR: --client-cert-pem is required";  errors=$((errors+1)); }
[[ $errors -gt 0 ]] && usage

[[ ! -f "$PKCS12_BUNDLE" ]]   && { echo "ERROR: PKCS12 bundle not found: $PKCS12_BUNDLE"; exit 1; }
[[ ! -f "$CA_PEM" ]]          && { echo "ERROR: CA PEM not found: $CA_PEM"; exit 1; }
[[ ! -f "$CLIENT_CERT_PEM" ]] && { echo "ERROR: Client cert PEM not found: $CLIENT_CERT_PEM"; exit 1; }

command -v jq     &>/dev/null || { echo "ERROR: jq is required but not installed";     exit 1; }
command -v curl   &>/dev/null || { echo "ERROR: curl is required but not installed";   exit 1; }
command -v base64 &>/dev/null || { echo "ERROR: base64 is required but not installed"; exit 1; }

# Apply TOKEN_PASSWORD default
[[ -z "$TOKEN_PASSWORD" ]] && TOKEN_PASSWORD="$PKCS12_PASSWORD"

# Build the ssl-client-cert header value: extract only the base64 body between
# BEGIN/END CERTIFICATE markers (ignoring any human-readable dump before them),
# then URL-encode the result (only +, / and = require encoding).
_CERT_B64=$(sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/p' "$CLIENT_CERT_PEM" \
  | grep -v "^-----" | tr -d '\n\r')
CLIENT_CERT_HEADER_VAL=$(printf '%s' "$_CERT_B64" | sed 's/+/%2B/g; s|/|%2F|g; s/=/%3D/g')
unset _CERT_B64

# ─── Helpers ──────────────────────────────────────────────────────────────────
# ilm_curl METHOD PATH [-d BODY]
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

log() { echo "==> $*" >&2; }
ok()  { echo "    OK: $*" >&2; }

die() { echo "ERROR: $*" >&2; exit 1; }

# require_uuid RESPONSE CONTEXT — extracts .uuid from JSON; exits if missing or null
require_uuid() {
  local uuid
  uuid=$(echo "$1" | jq -r '.uuid // empty')
  [[ -z "$uuid" ]] && die "No UUID returned for $2. Response: $1"
  echo "$uuid"
}

# attr_uuid ATTRS_JSON EXPECTED_NAME EXPECTED_CONTENT_TYPE
# Looks up the uuid of an attribute by name + contentType (the stable contract).
# On mismatch, prints each received attribute so the script can be updated.
attr_uuid() {
  local attrs="$1" name="$2" content_type="$3"
  local uuid
  uuid=$(echo "$attrs" | jq -r \
    --arg n "$name" --arg ct "$content_type" \
    'first(.[] | select(.name==$n and .contentType==$ct) | .uuid) // empty')
  if [[ -z "$uuid" ]]; then
    echo "ERROR: Expected attribute  name='${name}'  contentType='${content_type}' — not found." >&2
    echo "       Received attributes:" >&2
    echo "$attrs" | jq -r \
      '.[] | "         name=\(.name)  contentType=\(.contentType // "(none)")  type=\(.type)"' >&2
    exit 1
  fi
  echo "$uuid"
}

# group_uuid ATTRS_JSON EXPECTED_NAME
# Like attr_uuid but for group-type attributes, which carry no contentType.
group_uuid() {
  local attrs="$1" name="$2"
  local uuid
  uuid=$(echo "$attrs" | jq -r \
    --arg n "$name" \
    'first(.[] | select(.name==$n and .type=="group") | .uuid) // empty')
  if [[ -z "$uuid" ]]; then
    echo "ERROR: Expected group attribute  name='${name}' — not found." >&2
    echo "       Received attributes:" >&2
    echo "$attrs" | jq -r \
      '.[] | "         name=\(.name)  contentType=\(.contentType // "(none)")  type=\(.type)"' >&2
    exit 1
  fi
  echo "$uuid"
}

# ─── Step 1: Connectors ───────────────────────────────────────────────────────
log "Creating credential-provider connector (port ${PORT_CRED_PROVIDER})..."
_RESP=$(ilm_curl POST /v2/connectors -d \
  "{\"name\":\"common-credential-provider\",\"url\":\"http://${CONNECTOR_HOST}:${PORT_CRED_PROVIDER}\",\"authType\":\"none\",\"customAttributes\":[],\"version\":\"v1\"}")
CRED_CONN_UUID=$(require_uuid "$_RESP" "common-credential-provider connector")
ok "common-credential-provider  $CRED_CONN_UUID"

log "Creating ejbca-ng connector (port ${PORT_EJBCA})..."
_RESP=$(ilm_curl POST /v2/connectors -d \
  "{\"name\":\"ejbca-ng-connector\",\"url\":\"http://${CONNECTOR_HOST}:${PORT_EJBCA}\",\"authType\":\"none\",\"customAttributes\":[],\"version\":\"v1\"}")
EJBCA_CONN_UUID=$(require_uuid "$_RESP" "ejbca-ng-connector connector")
ok "ejbca-ng-connector           $EJBCA_CONN_UUID"

log "Creating software-cryptography-provider connector (port ${PORT_CRYPTO_PROVIDER})..."
_RESP=$(ilm_curl POST /v2/connectors -d \
  "{\"name\":\"software-cryptography-provider\",\"url\":\"http://${CONNECTOR_HOST}:${PORT_CRYPTO_PROVIDER}\",\"authType\":\"none\",\"customAttributes\":[],\"version\":\"v1\"}")
CRYPTO_CONN_UUID=$(require_uuid "$_RESP" "software-cryptography-provider connector")
ok "software-cryptography-provider  $CRYPTO_CONN_UUID"

# ─── Step 2: CA certificate upload ───────────────────────────────────────────
log "Uploading CA certificate from $(basename "$CA_PEM")..."
# The API expects a base64-encoded DER or PEM; strip PEM headers and re-encode to plain base64
CA_CERT_B64=$(sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/p' "$CA_PEM" \
  | grep -v "^-----" | tr -d '\n\r')
_RESP=$(ilm_curl POST /v1/certificates/upload -d \
  "$(jq -n --arg cert "$CA_CERT_B64" '{certificate: $cert, customAttributes: []}')")
CA_CERT_UUID=$(require_uuid "$_RESP" "CA certificate upload")
ok "CA certificate  $CA_CERT_UUID"

log "Marking CA certificate as trusted..."
ilm_curl PATCH "/v1/certificates/${CA_CERT_UUID}" -d '{"trustedCa": true}' >/dev/null
ok "CA certificate marked trusted"

# ─── Step 3: Credential ───────────────────────────────────────────────────────
log "Fetching SoftKeyStore credential attribute definitions..."
CRED_ATTR_DEFS=$(ilm_curl GET \
  "/v1/connectors/${CRED_CONN_UUID}/attributes/credentialProvider/SoftKeyStore")
KS_TYPE_UUID=$(attr_uuid "$CRED_ATTR_DEFS" "keyStoreType"     "string")
KS_PASS_UUID=$(attr_uuid "$CRED_ATTR_DEFS" "keyStorePassword" "secret")
KS_FILE_UUID=$(attr_uuid "$CRED_ATTR_DEFS" "keyStore"         "file")

log "Creating credential '${CREDENTIAL_NAME}' from $(basename "$PKCS12_BUNDLE")..."
PKCS12_B64=$(base64 < "$PKCS12_BUNDLE" | tr -d '\n')
PKCS12_FILENAME=$(basename "$PKCS12_BUNDLE")

_RESP=$(ilm_curl POST /v1/credentials -d \
  "$(jq -n \
    --arg name        "$CREDENTIAL_NAME" \
    --arg connUuid    "$CRED_CONN_UUID" \
    --arg pass        "$PKCS12_PASSWORD" \
    --arg b64         "$PKCS12_B64" \
    --arg fname       "$PKCS12_FILENAME" \
    --arg ksTypeUuid  "$KS_TYPE_UUID" \
    --arg ksPassUuid  "$KS_PASS_UUID" \
    --arg ksFileUuid  "$KS_FILE_UUID" \
    '{
      name: $name,
      connectorUuid: $connUuid,
      kind: "SoftKeyStore",
      attributes: [
        {
          name: "keyStoreType",
          content: [{data: "PKCS12", reference: "PKCS12"}],
          contentType: "string",
          uuid: $ksTypeUuid,
          version: "v2"
        },
        {
          name: "keyStorePassword",
          content: [{data: {secret: $pass}}],
          contentType: "secret",
          uuid: $ksPassUuid,
          version: "v2"
        },
        {
          name: "keyStore",
          content: [{data: {content: $b64, fileName: $fname, mimeType: "application/x-pkcs12"}}],
          contentType: "file",
          uuid: $ksFileUuid,
          version: "v2"
        }
      ],
      customAttributes: []
    }')")
CRED_UUID=$(require_uuid "$_RESP" "credential '${CREDENTIAL_NAME}'")
ok "credential  $CRED_UUID"

# ─── Step 4: Authority ────────────────────────────────────────────────────────
log "Fetching EJBCA authority attribute definitions..."
AUTH_ATTR_DEFS=$(ilm_curl GET \
  "/v1/connectors/${EJBCA_CONN_UUID}/attributes/authorityProvider/EJBCA")
AUTH_URL_UUID=$(attr_uuid  "$AUTH_ATTR_DEFS" "url"        "string")
AUTH_CRED_UUID=$(attr_uuid "$AUTH_ATTR_DEFS" "credential" "credential")

log "Creating EJBCA authority '${AUTHORITY_NAME}'..."
_RESP=$(ilm_curl POST /v1/authorities -d \
  "$(jq -n \
    --arg name         "$AUTHORITY_NAME" \
    --arg connUuid     "$EJBCA_CONN_UUID" \
    --arg credUuid     "$CRED_UUID" \
    --arg credName     "$CREDENTIAL_NAME" \
    --arg url          "$EJBCA_URL" \
    --arg authUrlUuid  "$AUTH_URL_UUID" \
    --arg authCredUuid "$AUTH_CRED_UUID" \
    '{
      name: $name,
      connectorUuid: $connUuid,
      kind: "EJBCA",
      attributes: [
        {
          name: "url",
          content: [{data: $url}],
          contentType: "string",
          uuid: $authUrlUuid,
          version: "v2"
        },
        {
          name: "credential",
          content: [{data: {uuid: $credUuid, name: $credName}, reference: $credName}],
          contentType: "credential",
          uuid: $authCredUuid,
          version: "v2"
        }
      ],
      customAttributes: []
    }')")
AUTH_UUID=$(require_uuid "$_RESP" "EJBCA authority '${AUTHORITY_NAME}'")
ok "authority  $AUTH_UUID"

# ─── Step 5: Token ────────────────────────────────────────────────────────────
log "Fetching SOFT token attribute definitions..."
TOKEN_ATTR_DEFS=$(ilm_curl GET \
  "/v1/connectors/${CRYPTO_CONN_UUID}/attributes/cryptographyProvider/SOFT")
TOK_ACTION_UUID=$(attr_uuid "$TOKEN_ATTR_DEFS" "data_createTokenAction" "string")
TOK_NAME_UUID=$(attr_uuid   "$TOKEN_ATTR_DEFS" "data_newTokenName"      "string")
TOK_CODE_UUID=$(attr_uuid   "$TOKEN_ATTR_DEFS" "data_tokenCode"         "secret")

log "Creating soft token '${TOKEN_NAME}'..."
_RESP=$(ilm_curl POST /v1/tokens -d \
  "$(jq -n \
    --arg name          "$TOKEN_NAME" \
    --arg connUuid      "$CRYPTO_CONN_UUID" \
    --arg pin           "$TOKEN_PASSWORD" \
    --arg tokActionUuid "$TOK_ACTION_UUID" \
    --arg tokNameUuid   "$TOK_NAME_UUID" \
    --arg tokCodeUuid   "$TOK_CODE_UUID" \
    '{
      name: $name,
      connectorUuid: $connUuid,
      kind: "SOFT",
      attributes: [
        {
          name: "data_createTokenAction",
          content: [{data: "new"}],
          contentType: "string",
          uuid: $tokActionUuid,
          version: "v2"
        },
        {
          name: "data_newTokenName",
          content: [{data: $name}],
          contentType: "string",
          uuid: $tokNameUuid,
          version: "v2"
        },
        {
          name: "data_tokenCode",
          content: [{data: {secret: $pin}}],
          contentType: "secret",
          uuid: $tokCodeUuid,
          version: "v2"
        }
      ],
      customAttributes: []
    }')")
TOKEN_UUID=$(require_uuid "$_RESP" "soft token '${TOKEN_NAME}'")
ok "token  $TOKEN_UUID"

# ─── Step 6: Token profile ────────────────────────────────────────────────────
log "Creating token profile '${TOKEN_PROFILE_NAME}'..."
_RESP=$(ilm_curl POST /v1/tokens/${TOKEN_UUID}/tokenProfiles -d \
  "$(jq -n --arg name "$TOKEN_PROFILE_NAME" \
    '{name: $name, description: "", attributes: [], customAttributes: [],
      usage: ["sign","verify","encrypt","decrypt"]}')")
TOKEN_PROFILE_UUID=$(require_uuid "$_RESP" "token profile '${TOKEN_PROFILE_NAME}'")
ok "token profile  $TOKEN_PROFILE_UUID"

log "Enabling token profile..."
ilm_curl PATCH "/v1/tokens/${TOKEN_UUID}/tokenProfiles/${TOKEN_PROFILE_UUID}/enable" \
  >/dev/null
ok "token profile enabled"

# ─── Step 7: Key pair ─────────────────────────────────────────────────────────
log "Fetching key pair attribute definitions..."
KEYPAIR_ATTR_DEFS=$(ilm_curl GET \
  "/v1/tokens/${TOKEN_UUID}/tokenProfiles/${TOKEN_PROFILE_UUID}/keys/keyPair/attributes")
KEY_ALIAS_UUID=$(attr_uuid       "$KEYPAIR_ATTR_DEFS" "data_keyAlias"     "string")
KEY_ALG_UUID=$(attr_uuid         "$KEYPAIR_ATTR_DEFS" "data_keyAlgorithm" "string")
KEY_SPEC_GROUP_UUID=$(group_uuid "$KEYPAIR_ATTR_DEFS" "group_keySpec")

log "Fetching RSA key-spec attributes via callback..."
KEY_SPEC_ATTRS=$(ilm_curl POST "/v1/keys/${TOKEN_PROFILE_UUID}/callback" -d \
  "$(jq -n --arg uuid "$KEY_SPEC_GROUP_UUID" \
    '{"uuid":$uuid,"name":"group_keySpec","pathVariable":{"algorithm":"RSA"},
      "requestParameter":{},"body":{},"filter":{}}')")
RSA_KEY_SIZE_UUID=$(attr_uuid "$KEY_SPEC_ATTRS" "data_rsaKeySize" "integer")

log "Creating RSA 2048 key pair '${KEY_NAME}'..."
_RESP=$(ilm_curl POST \
  "/v1/tokens/${TOKEN_UUID}/tokenProfiles/${TOKEN_PROFILE_UUID}/keys/keyPair" -d \
  "$(jq -n \
    --arg name            "$KEY_NAME" \
    --arg keyAliasUuid    "$KEY_ALIAS_UUID" \
    --arg keyAlgUuid      "$KEY_ALG_UUID" \
    --arg rsaKeySizeUuid  "$RSA_KEY_SIZE_UUID" \
    '{
      groupUuids: [],
      name: $name,
      description: "",
      attributes: [
        {
          name: "data_keyAlias",
          content: [{data: $name}],
          contentType: "string",
          uuid: $keyAliasUuid,
          version: "v2"
        },
        {
          name: "data_keyAlgorithm",
          content: [{data: "RSA", reference: "RSA"}],
          contentType: "string",
          uuid: $keyAlgUuid,
          version: "v2"
        },
        {
          name: "data_rsaKeySize",
          content: [{data: 2048, reference: "RSA_2048"}],
          contentType: "integer",
          uuid: $rsaKeySizeUuid,
          version: "v2"
        }
      ],
      customAttributes: []
    }')")
KEY_UUID=$(require_uuid "$_RESP" "RSA key pair '${KEY_NAME}'")
ok "key  $KEY_UUID"

log "Enabling key..."
ilm_curl PATCH "/v1/keys/${KEY_UUID}/enable" >/dev/null
ok "key enabled"

log "Fetching key details for private key item UUID..."
KEY_DETAILS=$(ilm_curl GET "/v1/keys/${KEY_UUID}")
PRIVATE_KEY_ITEM_UUID=$(echo "$KEY_DETAILS" | jq -r \
  'first(.items[] | select(.type == "Private") | .uuid) // empty')
if [[ -z "$PRIVATE_KEY_ITEM_UUID" ]]; then
  echo "ERROR: Could not find Private key item in key ${KEY_UUID}. Available items:" >&2
  echo "$KEY_DETAILS" | jq -r '.items[] | "  type=\(.type)  uuid=\(.uuid)"' >&2
  exit 1
fi
ok "private key item  $PRIVATE_KEY_ITEM_UUID"

# ─── Step 8: RA profile (with dynamic EJBCA profile lookup) ───────────────────
log "Fetching available RA profile attributes from authority..."
RA_ATTRS=$(ilm_curl GET "/v1/authorities/${AUTH_UUID}/attributes/raProfile")

# Extract attribute definition UUIDs from the schema
EE_PROFILE_ATTR_UUID=$(attr_uuid   "$RA_ATTRS" "endEntityProfile"       "object")
CERT_PROFILE_ATTR_UUID=$(attr_uuid "$RA_ATTRS" "certificateProfile"     "object")
CA_ATTR_UUID=$(attr_uuid           "$RA_ATTRS" "certificationAuthority" "object")
SEND_NOTIF_ATTR_UUID=$(attr_uuid   "$RA_ATTRS" "sendNotifications"      "boolean")
KEY_RECOVER_ATTR_UUID=$(attr_uuid  "$RA_ATTRS" "keyRecoverable"         "boolean")
USERNAME_GEN_ATTR_UUID=$(attr_uuid "$RA_ATTRS" "usernameGenMethod"      "string")

# The EJBCA internal authority instance UUID is embedded as a static callback mapping value
EJBCA_AUTHORITY_ID=$(echo "$RA_ATTRS" | jq -r '
  .[] | select(.name=="certificationAuthority") |
  .attributeCallback.mappings[] |
  select(.to=="authorityId" and has("value")) | .value')

# Resolve end entity profile ID by name
EE_PROFILE_ID=$(echo "$RA_ATTRS" | jq -r \
  --arg name "$EJBCA_EE_PROFILE" \
  '.[] | select(.name=="endEntityProfile") | .content[] | select(.data.name==$name) | .data.id')
[[ -z "$EE_PROFILE_ID" ]] && {
  echo "ERROR: End entity profile '$EJBCA_EE_PROFILE' not found. Available:" >&2
  echo "$RA_ATTRS" | jq -r '.[] | select(.name=="endEntityProfile") | .content[].data.name' >&2
  exit 1
}
ok "end-entity profile '$EJBCA_EE_PROFILE'  id=$EE_PROFILE_ID"

log "Resolving certificate profile '${EJBCA_CERT_PROFILE}'..."
CERT_PROFILES=$(ilm_curl POST "/v1/raProfiles/${AUTH_UUID}/callback" -d \
  "$(jq -n \
    --arg uuid   "$CERT_PROFILE_ATTR_UUID" \
    --arg authId "$EJBCA_AUTHORITY_ID" \
    --argjson eeId "$EE_PROFILE_ID" \
    '{"uuid":$uuid,"name":"certificateProfile","pathVariable":{"endEntityProfileId":$eeId,"authorityId":$authId},"requestParameter":{},"body":{},"filter":{}}')")
CERT_PROFILE_ID=$(echo "$CERT_PROFILES" | jq -r \
  --arg name "$EJBCA_CERT_PROFILE" '.[] | select(.data.name==$name) | .data.id')
[[ -z "$CERT_PROFILE_ID" ]] && {
  echo "ERROR: Certificate profile '$EJBCA_CERT_PROFILE' not found. Available:" >&2
  echo "$CERT_PROFILES" | jq -r '.[].data.name' >&2
  exit 1
}
ok "certificate profile '$EJBCA_CERT_PROFILE'  id=$CERT_PROFILE_ID"

log "Resolving CA '${EJBCA_CA_NAME}'..."
CA_LIST=$(ilm_curl POST "/v1/raProfiles/${AUTH_UUID}/callback" -d \
  "$(jq -n \
    --arg uuid   "$CA_ATTR_UUID" \
    --arg authId "$EJBCA_AUTHORITY_ID" \
    --argjson eeId "$EE_PROFILE_ID" \
    '{"uuid":$uuid,"name":"certificationAuthority","pathVariable":{"authorityId":$authId,"endEntityProfileId":$eeId},"requestParameter":{},"body":{},"filter":{}}')")
EJBCA_CA_ID=$(echo "$CA_LIST" | jq -r \
  --arg name "$EJBCA_CA_NAME" '.[] | select(.data.name==$name) | .data.id')
[[ -z "$EJBCA_CA_ID" ]] && {
  echo "ERROR: CA '$EJBCA_CA_NAME' not found. Available:" >&2
  echo "$CA_LIST" | jq -r '.[].data.name' >&2
  exit 1
}
ok "CA '$EJBCA_CA_NAME'  id=$EJBCA_CA_ID"

log "Creating RA profile '${RA_PROFILE_NAME}'..."
_RESP=$(ilm_curl POST "/v1/authorities/${AUTH_UUID}/raProfiles" -d \
  "$(jq -n \
    --arg  name          "$RA_PROFILE_NAME" \
    --arg  eeProfileName "$EJBCA_EE_PROFILE" \
    --argjson eeId       "$EE_PROFILE_ID" \
    --arg  eeAttrUuid    "$EE_PROFILE_ATTR_UUID" \
    --arg  cpName        "$EJBCA_CERT_PROFILE" \
    --argjson cpId       "$CERT_PROFILE_ID" \
    --arg  cpAttrUuid    "$CERT_PROFILE_ATTR_UUID" \
    --arg  caName        "$EJBCA_CA_NAME" \
    --argjson caId       "$EJBCA_CA_ID" \
    --arg  caAttrUuid    "$CA_ATTR_UUID" \
    --arg  snAttrUuid    "$SEND_NOTIF_ATTR_UUID" \
    --arg  krAttrUuid    "$KEY_RECOVER_ATTR_UUID" \
    --arg  ugAttrUuid    "$USERNAME_GEN_ATTR_UUID" \
    '{
      name: $name,
      description: "",
      attributes: [
        {
          name: "endEntityProfile",
          content: [{data: {id: $eeId, name: $eeProfileName}, reference: $eeProfileName}],
          contentType: "object",
          uuid: $eeAttrUuid,
          version: "v2"
        },
        {
          name: "certificateProfile",
          content: [{data: {id: $cpId, name: $cpName}, reference: $cpName}],
          contentType: "object",
          uuid: $cpAttrUuid,
          version: "v2"
        },
        {
          name: "certificationAuthority",
          content: [{data: {id: $caId, name: $caName}, reference: $caName}],
          contentType: "object",
          uuid: $caAttrUuid,
          version: "v2"
        },
        {
          name: "sendNotifications",
          content: [{data: false}],
          contentType: "boolean",
          uuid: $snAttrUuid,
          version: "v2"
        },
        {
          name: "keyRecoverable",
          content: [{data: false}],
          contentType: "boolean",
          uuid: $krAttrUuid,
          version: "v2"
        },
        {
          name: "usernameGenMethod",
          content: [{data: "CN"}],
          contentType: "string",
          uuid: $ugAttrUuid,
          version: "v2"
        }
      ],
      customAttributes: []
    }')")
RA_PROFILE_UUID=$(require_uuid "$_RESP" "RA profile '${RA_PROFILE_NAME}'")
ok "RA profile  $RA_PROFILE_UUID"

log "Enabling RA profile..."
ilm_curl PATCH \
  "/v1/authorities/${AUTH_UUID}/raProfiles/${RA_PROFILE_UUID}/enable" >/dev/null
ok "RA profile enabled"

# ─── Step 9: Issue TSA certificate ────────────────────────────────────────────
log "Fetching CSR attribute definitions..."
CSR_ATTRS=$(ilm_curl GET "/v1/certificates/csr/attributes")
CN_UUID=$(attr_uuid "$CSR_ATTRS" "commonName" "string")

log "Fetching signature attribute definitions..."
SIG_ATTRS=$(ilm_curl GET \
  "/v1/operations/tokens/${TOKEN_UUID}/tokenProfiles/${TOKEN_PROFILE_UUID}/keys/${KEY_UUID}/items/${PRIVATE_KEY_ITEM_UUID}/signature/RSA/attributes")
SIG_SCHEME_UUID=$(attr_uuid "$SIG_ATTRS" "data_rsaSigScheme" "string")
SIG_DIGEST_UUID=$(attr_uuid "$SIG_ATTRS" "data_sigDigest"    "string")

log "Issuing TSA certificate  CN=${CERTIFICATE_DN}..."
_RESP=$(ilm_curl POST \
  "/v2/operations/authorities/${AUTH_UUID}/raProfiles/${RA_PROFILE_UUID}/certificates" -d \
  "$(jq -n \
    --arg cn               "$CERTIFICATE_DN" \
    --arg keyUuid          "$KEY_UUID" \
    --arg tokenProfileUuid "$TOKEN_PROFILE_UUID" \
    --arg cnUuid           "$CN_UUID" \
    --arg sigSchemeUuid    "$SIG_SCHEME_UUID" \
    --arg sigDigestUuid    "$SIG_DIGEST_UUID" \
    '{
      format: "pkcs10",
      request: "",
      attributes: [],
      csrAttributes: [
        {
          name: "commonName",
          content: [{data: $cn}],
          contentType: "string",
          uuid: $cnUuid,
          version: "v2"
        }
      ],
      signatureAttributes: [
        {
          name: "data_rsaSigScheme",
          content: [{data: "PKCS1-v1_5", reference: "PKCS#1 v1.5"}],
          contentType: "string",
          uuid: $sigSchemeUuid,
          version: "v2"
        },
        {
          name: "data_sigDigest",
          content: [{data: "SHA-384", reference: "SHA-384"}],
          contentType: "string",
          uuid: $sigDigestUuid,
          version: "v2"
        }
      ],
      keyUuid: $keyUuid,
      tokenProfileUuid: $tokenProfileUuid,
      customAttributes: []
    }')")
ISSUED_CERT_UUID=$(require_uuid "$_RESP" "TSA certificate CN=${CERTIFICATE_DN}")
ok "issued certificate  $ISSUED_CERT_UUID"

# ─── Step 10: TSP profile ─────────────────────────────────────────────────────
log "Creating TSP profile '${TSP_PROFILE_NAME}'..."
_RESP=$(ilm_curl POST /v1/tspProfiles -d \
  "$(jq -n --arg name "$TSP_PROFILE_NAME" '{name: $name, customAttributes: []}')")
TSP_PROFILE_UUID=$(require_uuid "$_RESP" "TSP profile '${TSP_PROFILE_NAME}'")
ok "TSP profile  $TSP_PROFILE_UUID"

log "Enabling TSP profile..."
ilm_curl PATCH "/v1/tspProfiles/${TSP_PROFILE_UUID}/enable" >/dev/null
ok "TSP profile enabled"

# ─── Summary ──────────────────────────────────────────────────────────────────
cat <<EOF

Setup complete. Created resources:

  connector     common-credential-provider      $CRED_CONN_UUID
  connector     ejbca-ng-connector              $EJBCA_CONN_UUID
  connector     software-cryptography-provider  $CRYPTO_CONN_UUID
  ca-cert       $(basename "$CA_PEM") (trusted) $CA_CERT_UUID
  credential    $CREDENTIAL_NAME                $CRED_UUID
  authority     $AUTHORITY_NAME                 $AUTH_UUID
  token         $TOKEN_NAME                     $TOKEN_UUID
  token-profile $TOKEN_PROFILE_NAME             $TOKEN_PROFILE_UUID
  key           $KEY_NAME                       $KEY_UUID
  ra-profile    $RA_PROFILE_NAME                $RA_PROFILE_UUID
  certificate   CN=$CERTIFICATE_DN              $ISSUED_CERT_UUID
  tsp-profile   $TSP_PROFILE_NAME               $TSP_PROFILE_UUID
EOF
