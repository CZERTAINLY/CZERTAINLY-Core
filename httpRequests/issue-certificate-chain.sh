#!/usr/bin/env bash
# issue-certificate-chain.sh
#
# Sets up the ILM infrastructure and issues a leaf certificate via EJBCA so
# that the resulting certificate record carries associations with all major
# ILM entities that participate in certificate-chain DB retrieval:
#
#   • RA Profile           – set automatically when the certificate is issued
#                            through an RA profile
#   • Group                – a new group is created and explicitly associated
#                            with both the issued certificate and the CA cert
#   • Key                  – set automatically when the CSR is signed with an
#                            ILM-managed cryptographic key
#   • Key Item             – the specific private-key item UUID is embedded in
#                            the certificate request; the signing-attributes
#                            call pins the exact key item used
#   • CertificateRequest   – created automatically by ILM when the CSR is
#                            submitted for issuance
#
# The CA certificate uploaded via --ca-pem is additionally associated with
# the same group and RA profile to maximise entity coverage across the chain.
#
# Requires: curl, jq, base64

set -euo pipefail

# --- Defaults -----------------------------------------------------------------
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
EJBCA_EE_PROFILE="DemoTLSServerEndEntityProfile"
EJBCA_CERT_PROFILE="DemoTLSServerEECertificateProfile"
EJBCA_CA_NAME="DemoRootCA_2307RSA"

CREDENTIAL_NAME="ejbca.3key.company"
AUTHORITY_NAME="ejbca.3key.company"
TOKEN_NAME="chainTest"
TOKEN_PROFILE_NAME="chainTest"
KEY_NAME="chainTestRsa"
RA_PROFILE_NAME="chainTest"
GROUP_NAME="chainTestGroup"

CERT_POLL_ATTEMPTS=20
CERT_POLL_INTERVAL=1

# --- Result variables (populated by setup functions) --------------------------
CLIENT_CERT_HEADER_VAL=""
CRED_CONN_UUID=""
EJBCA_CONN_UUID=""
CRYPTO_CONN_UUID=""
CA_CERT_UUID=""
CRED_UUID=""
AUTH_UUID=""
TOKEN_UUID=""
TOKEN_PROFILE_UUID=""
KEY_UUID=""
PRIVATE_KEY_ITEM_UUID=""
RA_PROFILE_UUID=""
GROUP_UUID=""
ISSUED_CERT_UUID=""

# --- Usage --------------------------------------------------------------------
usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Required:
  --client-cert-pem FILE      ILM admin client certificate PEM
  --pkcs12-bundle FILE        Path to PKCS12 bundle with EJBCA client credentials
  --ca-pem FILE               Path to CA certificate PEM to upload and mark trusted
  --certificate-dn DN         Subject CN for the issued leaf certificate

Connector options (defaults: localhost, ports 8201/8210/8230):
  --connector-host HOST       Hostname for connectors as seen from ILM server
  --port-cred-provider PORT   common-credential-provider port     (default: 8201)
  --port-ejbca PORT           ejbca-ng-connector port             (default: 8210)
  --port-crypto-provider PORT software-cryptography-provider port (default: 8230)

Credential/token options:
  --pkcs12-password PASS      PKCS12 bundle password              (default: 00000000)
  --token-password PASS       Soft token PIN                      (default: same as pkcs12-password)

ILM API auth:
  --ilm-host URL              URL of ILM API                      (default: http://localhost:8200)

EJBCA options:
  --ejbca-url URL             EJBCA WSDL URL      (default: https://ejbca.3key.company/ejbca/ejbcaws/ejbcaws?wsdl)
  --ejbca-ca NAME             Issuing CA name     (default: DemoRootCA_2307RSA)
  --ejbca-ee-profile NAME     End entity profile  (default: DemoTLSServerEndEntityProfile)
  --ejbca-cert-profile NAME   Certificate profile (default: DemoTLSServerEECertificateProfile)

Object names (usually no need to change):
  --credential-name NAME      (default: ejbca.3key.company)
  --authority-name NAME       (default: ejbca.3key.company)
  --token-name NAME           (default: chainTest)
  --token-profile-name NAME   (default: chainTest)
  --key-name NAME             (default: chainTestRsa)
  --ra-profile-name NAME      (default: chainTest)
  --group-name NAME           (default: chainTestGroup)

Certificate polling:
  --cert-poll-attempts N      Max poll attempts for certificate issuance (default: 20)
  --cert-poll-interval N      Seconds between poll attempts              (default: 1)
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

# attr_uuid ATTRS_JSON EXPECTED_NAME EXPECTED_CONTENT_TYPE
# Looks up the uuid of an attribute by name + contentType.
attr_uuid() {
  local attrs="$1" name="$2" content_type="$3"
  local uuid
  uuid=$(echo "$attrs" | jq -r \
    --arg n "$name" --arg ct "$content_type" \
    'first(.[] | select(.name==$n and .contentType==$ct) | .uuid) // empty')
  if [[ -z "$uuid" ]]; then
    echo "ERROR: Expected attribute  name='${name}'  contentType='${content_type}' -- not found." >&2
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
    echo "ERROR: Expected group attribute  name='${name}' -- not found." >&2
    echo "       Received attributes:" >&2
    echo "$attrs" | jq -r \
      '.[] | "         name=\(.name)  contentType=\(.contentType // "(none)")  type=\(.type)"' >&2
    exit 1
  fi
  echo "$uuid"
}

# --- Argument parsing ---------------------------------------------------------
parse_args() {
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
      --group-name)             GROUP_NAME="$2";             shift 2 ;;
      --cert-poll-attempts)     CERT_POLL_ATTEMPTS="$2";     shift 2 ;;
      --cert-poll-interval)     CERT_POLL_INTERVAL="$2";     shift 2 ;;
      --help|-h)                usage ;;
      *) echo "Unknown option: $1"; usage ;;
    esac
  done
}

# --- Validation ---------------------------------------------------------------
validate() {
  local errors=0
  [[ -z "$CLIENT_CERT_PEM" ]] && { echo "ERROR: --client-cert-pem is required"; errors=$((errors+1)); }
  [[ -z "$PKCS12_BUNDLE"   ]] && { echo "ERROR: --pkcs12-bundle is required";   errors=$((errors+1)); }
  [[ -z "$CA_PEM"          ]] && { echo "ERROR: --ca-pem is required";           errors=$((errors+1)); }
  [[ -z "$CERTIFICATE_DN"  ]] && { echo "ERROR: --certificate-dn is required";   errors=$((errors+1)); }
  [[ $errors -gt 0 ]] && usage

  [[ ! -f "$CLIENT_CERT_PEM" ]] && die "Client cert PEM not found: $CLIENT_CERT_PEM"
  [[ ! -f "$PKCS12_BUNDLE"   ]] && die "PKCS12 bundle not found: $PKCS12_BUNDLE"
  [[ ! -f "$CA_PEM"          ]] && die "CA PEM not found: $CA_PEM"

  command -v curl   &>/dev/null || die "curl is required but not installed"
  command -v jq     &>/dev/null || die "jq is required but not installed"
  command -v base64 &>/dev/null || die "base64 is required but not installed"

  [[ -z "$TOKEN_PASSWORD" ]] && TOKEN_PASSWORD="$PKCS12_PASSWORD"

  local _cert_b64
  _cert_b64=$(sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/p' "$CLIENT_CERT_PEM" \
    | grep -v "^-----" | tr -d '\n\r')
  CLIENT_CERT_HEADER_VAL=$(printf '%s' "$_cert_b64" | sed 's/+/%2B/g; s|/|%2F|g; s/=/%3D/g')
}

# --- Step 1: Connectors -------------------------------------------------------
setup_connectors() {
  local _resp

  log "Creating common-credential-provider connector..."
  _resp=$(ilm_curl POST /v1/connectors -d \
    "$(jq -n \
      --arg host "$CONNECTOR_HOST" \
      --arg port "$PORT_CRED_PROVIDER" \
      '{
        name: "common-credential-provider",
        url: ("http://" + $host + ":" + $port),
        authType: "none"
      }')")
  CRED_CONN_UUID=$(require_uuid "$_resp" "common-credential-provider connector")
  ok "common-credential-provider connector  $CRED_CONN_UUID"

  log "Creating ejbca-ng-connector..."
  _resp=$(ilm_curl POST /v1/connectors -d \
    "$(jq -n \
      --arg host "$CONNECTOR_HOST" \
      --arg port "$PORT_EJBCA" \
      '{
        name: "ejbca-ng-connector",
        url: ("http://" + $host + ":" + $port),
        authType: "none"
      }')")
  EJBCA_CONN_UUID=$(require_uuid "$_resp" "ejbca-ng-connector")
  ok "ejbca-ng-connector  $EJBCA_CONN_UUID"

  log "Creating software-cryptography-provider connector..."
  _resp=$(ilm_curl POST /v1/connectors -d \
    "$(jq -n \
      --arg host "$CONNECTOR_HOST" \
      --arg port "$PORT_CRYPTO_PROVIDER" \
      '{
        name: "software-cryptography-provider",
        url: ("http://" + $host + ":" + $port),
        authType: "none"
      }')")
  CRYPTO_CONN_UUID=$(require_uuid "$_resp" "software-cryptography-provider connector")
  ok "software-cryptography-provider  $CRYPTO_CONN_UUID"
}

# --- Step 2: Upload and trust CA certificate ----------------------------------
upload_ca_cert() {
  local cert_b64 _resp http_code tmp fingerprint search_resp

  log "Uploading CA certificate from ${CA_PEM}..."
  cert_b64=$(openssl x509 -in "$CA_PEM" -outform DER 2>/dev/null | base64 | tr -d '\n')

  tmp=$(mktemp)
  http_code=$(curl -s -o "$tmp" -w "%{http_code}" -X POST \
    -H "ssl-client-cert: ${CLIENT_CERT_HEADER_VAL}" \
    -H "content-type: application/json" \
    "${ILM_HOST}/api/v1/certificates/upload" \
    -d "$(jq -n --arg cert "$cert_b64" '{"certificate": $cert, "customAttributes": []}')")
  _resp=$(<"$tmp"); rm -f "$tmp"

  if [[ "$http_code" -eq 409 ]]; then
    fingerprint=$(echo "$_resp" | jq -r '.message // empty' \
      | sed 's/.*fingerprint //')
    [[ -z "$fingerprint" ]] && \
      die "CA certificate already exists (HTTP 409) but could not parse fingerprint from: ${_resp}"

    log "CA certificate already exists (fingerprint ${fingerprint}) -- looking up existing UUID..."
    search_resp=$(ilm_curl POST /v1/certificates -d \
      "$(jq -n --arg fp "$fingerprint" \
        '{"filters":[{"fieldSource":"property","fieldIdentifier":"FINGERPRINT","condition":"EQUALS","value":$fp}],"itemsPerPage":1,"pageNumber":1}')")
    CA_CERT_UUID=$(echo "$search_resp" | jq -r '.certificates[0].uuid // empty')
    [[ -z "$CA_CERT_UUID" ]] && \
      die "CA certificate not found by fingerprint '${fingerprint}'. Search response: ${search_resp}"
    ok "CA certificate already present  $CA_CERT_UUID"
  elif [[ "$http_code" -lt 200 || "$http_code" -ge 300 ]]; then
    die "HTTP ${http_code} on POST /api/v1/certificates/upload: ${_resp}"
  else
    CA_CERT_UUID=$(require_uuid "$_resp" "CA certificate")
    ok "CA certificate  $CA_CERT_UUID"
  fi

  log "Marking CA certificate as trusted..."
  ilm_curl PATCH "/v1/certificates/${CA_CERT_UUID}" -d \
    '{"trustedCa": true}' >/dev/null
  ok "CA certificate marked trusted"
}

# --- Step 3: Credential -------------------------------------------------------
setup_credential() {
  local cred_attr_defs ks_type_uuid ks_pass_uuid ks_file_uuid pkcs12_b64 pkcs12_filename _resp

  log "Fetching SoftKeyStore credential attribute definitions..."
  cred_attr_defs=$(ilm_curl GET \
    "/v1/connectors/${CRED_CONN_UUID}/attributes/credentialProvider/SoftKeyStore")
  ks_type_uuid=$(attr_uuid "$cred_attr_defs" "keyStoreType"     "string")
  ks_pass_uuid=$(attr_uuid "$cred_attr_defs" "keyStorePassword" "secret")
  ks_file_uuid=$(attr_uuid "$cred_attr_defs" "keyStore"         "file")

  pkcs12_b64=$(base64 < "$PKCS12_BUNDLE" | tr -d '\n')
  pkcs12_filename=$(basename "$PKCS12_BUNDLE")

  log "Creating credential '${CREDENTIAL_NAME}'..."
  _resp=$(ilm_curl POST /v1/credentials -d \
    "$(jq -n \
      --arg name        "$CREDENTIAL_NAME" \
      --arg connUuid    "$CRED_CONN_UUID" \
      --arg pass        "$PKCS12_PASSWORD" \
      --arg b64         "$pkcs12_b64" \
      --arg fname       "$pkcs12_filename" \
      --arg ksTypeUuid  "$ks_type_uuid" \
      --arg ksPassUuid  "$ks_pass_uuid" \
      --arg ksFileUuid  "$ks_file_uuid" \
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
  CRED_UUID=$(require_uuid "$_resp" "credential '${CREDENTIAL_NAME}'")
  ok "credential  $CRED_UUID"
}

# --- Step 4: EJBCA authority --------------------------------------------------
setup_authority() {
  local auth_attr_defs auth_url_uuid auth_cred_uuid _resp

  log "Fetching EJBCA authority attribute definitions..."
  auth_attr_defs=$(ilm_curl GET \
    "/v1/connectors/${EJBCA_CONN_UUID}/attributes/authorityProvider/EJBCA")
  auth_url_uuid=$(attr_uuid  "$auth_attr_defs" "url"        "string")
  auth_cred_uuid=$(attr_uuid "$auth_attr_defs" "credential" "credential")

  log "Creating EJBCA authority '${AUTHORITY_NAME}'..."
  _resp=$(ilm_curl POST /v1/authorities -d \
    "$(jq -n \
      --arg name         "$AUTHORITY_NAME" \
      --arg connUuid     "$EJBCA_CONN_UUID" \
      --arg credUuid     "$CRED_UUID" \
      --arg credName     "$CREDENTIAL_NAME" \
      --arg url          "$EJBCA_URL" \
      --arg authUrlUuid  "$auth_url_uuid" \
      --arg authCredUuid "$auth_cred_uuid" \
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
  AUTH_UUID=$(require_uuid "$_resp" "authority '${AUTHORITY_NAME}'")
  ok "authority  $AUTH_UUID"
}

# --- Step 5: Soft token -------------------------------------------------------
setup_token() {
  local token_attr_defs tok_action_uuid tok_name_uuid tok_code_uuid _resp

  log "Fetching token attribute definitions..."
  token_attr_defs=$(ilm_curl GET \
    "/v1/connectors/${CRYPTO_CONN_UUID}/attributes/cryptographyProvider/SOFT")
  tok_action_uuid=$(attr_uuid "$token_attr_defs" "data_createTokenAction" "string")
  tok_name_uuid=$(attr_uuid   "$token_attr_defs" "data_newTokenName"      "string")
  tok_code_uuid=$(attr_uuid   "$token_attr_defs" "data_tokenCode"         "secret")

  log "Creating soft token '${TOKEN_NAME}'..."
  _resp=$(ilm_curl POST /v1/tokens -d \
    "$(jq -n \
      --arg name          "$TOKEN_NAME" \
      --arg connUuid      "$CRYPTO_CONN_UUID" \
      --arg pin           "$TOKEN_PASSWORD" \
      --arg tokActionUuid "$tok_action_uuid" \
      --arg tokNameUuid   "$tok_name_uuid" \
      --arg tokCodeUuid   "$tok_code_uuid" \
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
  TOKEN_UUID=$(require_uuid "$_resp" "token '${TOKEN_NAME}'")
  ok "token  $TOKEN_UUID"
}

# --- Step 6: Token profile ----------------------------------------------------
setup_token_profile() {
  local _resp

  log "Creating token profile '${TOKEN_PROFILE_NAME}'..."
  _resp=$(ilm_curl POST /v1/tokens/${TOKEN_UUID}/tokenProfiles -d \
    "$(jq -n --arg name "$TOKEN_PROFILE_NAME" \
      '{name: $name, description: "", attributes: [], customAttributes: [],
        usage: ["sign","verify","encrypt","decrypt"]}')")
  TOKEN_PROFILE_UUID=$(require_uuid "$_resp" "token profile '${TOKEN_PROFILE_NAME}'")
  ok "token profile  $TOKEN_PROFILE_UUID"

  log "Enabling token profile..."
  ilm_curl PATCH "/v1/tokens/${TOKEN_UUID}/tokenProfiles/${TOKEN_PROFILE_UUID}/enable" \
    >/dev/null
  ok "token profile enabled"
}

# --- Step 7: RSA 2048 key pair ------------------------------------------------
setup_key_pair() {
  local keypair_attr_defs key_alias_uuid key_alg_uuid key_spec_group_uuid _resp
  local key_spec_attrs rsa_key_size_uuid key_details

  log "Fetching key pair attribute definitions..."
  keypair_attr_defs=$(ilm_curl GET \
    "/v1/tokens/${TOKEN_UUID}/tokenProfiles/${TOKEN_PROFILE_UUID}/keys/keyPair/attributes")
  key_alias_uuid=$(attr_uuid       "$keypair_attr_defs" "data_keyAlias"     "string")
  key_alg_uuid=$(attr_uuid         "$keypair_attr_defs" "data_keyAlgorithm" "string")
  key_spec_group_uuid=$(group_uuid "$keypair_attr_defs" "group_keySpec")

  log "Fetching RSA key-spec attributes via callback..."
  key_spec_attrs=$(ilm_curl POST "/v1/keys/${TOKEN_PROFILE_UUID}/callback" -d \
    "$(jq -n --arg uuid "$key_spec_group_uuid" \
      '{"uuid":$uuid,"name":"group_keySpec","pathVariable":{"algorithm":"RSA"},
        "requestParameter":{},"body":{},"filter":{}}')")
  rsa_key_size_uuid=$(attr_uuid "$key_spec_attrs" "data_rsaKeySize" "integer")

  log "Creating RSA 2048 key pair '${KEY_NAME}'..."
  _resp=$(ilm_curl POST \
    "/v1/tokens/${TOKEN_UUID}/tokenProfiles/${TOKEN_PROFILE_UUID}/keys/keyPair" -d \
    "$(jq -n \
      --arg name            "$KEY_NAME" \
      --arg keyAliasUuid    "$key_alias_uuid" \
      --arg keyAlgUuid      "$key_alg_uuid" \
      --arg rsaKeySizeUuid  "$rsa_key_size_uuid" \
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
  KEY_UUID=$(require_uuid "$_resp" "RSA key pair '${KEY_NAME}'")
  ok "key  $KEY_UUID"

  log "Enabling key..."
  ilm_curl PATCH "/v1/keys/${KEY_UUID}/enable" >/dev/null
  ok "key enabled"

  log "Fetching key details for private key item UUID..."
  key_details=$(ilm_curl GET "/v1/keys/${KEY_UUID}")
  PRIVATE_KEY_ITEM_UUID=$(echo "$key_details" | jq -r \
    'first(.items[] | select(.type == "Private") | .uuid) // empty')
  if [[ -z "$PRIVATE_KEY_ITEM_UUID" ]]; then
    echo "ERROR: Could not find Private key item in key ${KEY_UUID}. Available items:" >&2
    echo "$key_details" | jq -r '.items[] | "  type=\(.type)  uuid=\(.uuid)"' >&2
    exit 1
  fi
  ok "private key item  $PRIVATE_KEY_ITEM_UUID"
}

# --- Step 8: RA profile (with dynamic EJBCA profile lookup) -------------------
setup_ra_profile() {
  local _resp ra_attrs ee_profile_attr_uuid cert_profile_attr_uuid ca_attr_uuid
  local send_notif_attr_uuid key_recover_attr_uuid username_gen_attr_uuid
  local ejbca_authority_id ee_profile_id cert_profiles cert_profile_id ca_list ejbca_ca_id

  log "Fetching available RA profile attributes from authority..."
  ra_attrs=$(ilm_curl GET "/v1/authorities/${AUTH_UUID}/attributes/raProfile")

  ee_profile_attr_uuid=$(attr_uuid   "$ra_attrs" "endEntityProfile"       "object")
  cert_profile_attr_uuid=$(attr_uuid "$ra_attrs" "certificateProfile"     "object")
  ca_attr_uuid=$(attr_uuid           "$ra_attrs" "certificationAuthority" "object")
  send_notif_attr_uuid=$(attr_uuid   "$ra_attrs" "sendNotifications"      "boolean")
  key_recover_attr_uuid=$(attr_uuid  "$ra_attrs" "keyRecoverable"         "boolean")
  username_gen_attr_uuid=$(attr_uuid "$ra_attrs" "usernameGenMethod"      "string")

  ejbca_authority_id=$(echo "$ra_attrs" | jq -r '
    .[] | select(.name=="certificationAuthority") |
    .attributeCallback.mappings[] |
    select(.to=="authorityId" and has("value")) | .value')

  ee_profile_id=$(echo "$ra_attrs" | jq -r \
    --arg name "$EJBCA_EE_PROFILE" \
    '.[] | select(.name=="endEntityProfile") | .content[] | select(.data.name==$name) | .data.id')
  [[ -z "$ee_profile_id" ]] && {
    echo "ERROR: End entity profile '$EJBCA_EE_PROFILE' not found. Available:" >&2
    echo "$ra_attrs" | jq -r '.[] | select(.name=="endEntityProfile") | .content[].data.name' >&2
    exit 1
  }
  ok "end-entity profile '$EJBCA_EE_PROFILE'  id=$ee_profile_id"

  log "Resolving certificate profile '${EJBCA_CERT_PROFILE}'..."
  cert_profiles=$(ilm_curl POST "/v1/raProfiles/${AUTH_UUID}/callback" -d \
    "$(jq -n \
      --arg uuid   "$cert_profile_attr_uuid" \
      --arg authId "$ejbca_authority_id" \
      --argjson eeId "$ee_profile_id" \
      '{"uuid":$uuid,"name":"certificateProfile","pathVariable":{"endEntityProfileId":$eeId,"authorityId":$authId},"requestParameter":{},"body":{},"filter":{}}')")
  cert_profile_id=$(echo "$cert_profiles" | jq -r \
    --arg name "$EJBCA_CERT_PROFILE" '.[] | select(.data.name==$name) | .data.id')
  [[ -z "$cert_profile_id" ]] && {
    echo "ERROR: Certificate profile '$EJBCA_CERT_PROFILE' not found. Available:" >&2
    echo "$cert_profiles" | jq -r '.[].data.name' >&2
    exit 1
  }
  ok "certificate profile '$EJBCA_CERT_PROFILE'  id=$cert_profile_id"

  log "Resolving CA '${EJBCA_CA_NAME}'..."
  ca_list=$(ilm_curl POST "/v1/raProfiles/${AUTH_UUID}/callback" -d \
    "$(jq -n \
      --arg uuid   "$ca_attr_uuid" \
      --arg authId "$ejbca_authority_id" \
      --argjson eeId "$ee_profile_id" \
      '{"uuid":$uuid,"name":"certificationAuthority","pathVariable":{"authorityId":$authId,"endEntityProfileId":$eeId},"requestParameter":{},"body":{},"filter":{}}')")
  ejbca_ca_id=$(echo "$ca_list" | jq -r \
    --arg name "$EJBCA_CA_NAME" '.[] | select(.data.name==$name) | .data.id')
  [[ -z "$ejbca_ca_id" ]] && {
    echo "ERROR: CA '$EJBCA_CA_NAME' not found. Available:" >&2
    echo "$ca_list" | jq -r '.[].data.name' >&2
    exit 1
  }
  ok "CA '$EJBCA_CA_NAME'  id=$ejbca_ca_id"

  log "Creating RA profile '${RA_PROFILE_NAME}'..."
  _resp=$(ilm_curl POST "/v1/authorities/${AUTH_UUID}/raProfiles" -d \
    "$(jq -n \
      --arg  name          "$RA_PROFILE_NAME" \
      --arg  eeProfileName "$EJBCA_EE_PROFILE" \
      --argjson eeId       "$ee_profile_id" \
      --arg  eeAttrUuid    "$ee_profile_attr_uuid" \
      --arg  cpName        "$EJBCA_CERT_PROFILE" \
      --argjson cpId       "$cert_profile_id" \
      --arg  cpAttrUuid    "$cert_profile_attr_uuid" \
      --arg  caName        "$EJBCA_CA_NAME" \
      --argjson caId       "$ejbca_ca_id" \
      --arg  caAttrUuid    "$ca_attr_uuid" \
      --arg  snAttrUuid    "$send_notif_attr_uuid" \
      --arg  krAttrUuid    "$key_recover_attr_uuid" \
      --arg  ugAttrUuid    "$username_gen_attr_uuid" \
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
  RA_PROFILE_UUID=$(require_uuid "$_resp" "RA profile '${RA_PROFILE_NAME}'")
  ok "RA profile  $RA_PROFILE_UUID"

  log "Enabling RA profile..."
  ilm_curl PATCH \
    "/v1/authorities/${AUTH_UUID}/raProfiles/${RA_PROFILE_UUID}/enable" >/dev/null
  ok "RA profile enabled"
}

# --- Step 9: Create group -----------------------------------------------------
create_group() {
  local _resp

  log "Creating group '${GROUP_NAME}'..."
  _resp=$(ilm_curl POST /v1/groups -d \
    "$(jq -n \
      --arg name "$GROUP_NAME" \
      --arg desc "Certificate chain test group – used to exercise group association on chain certificates" \
      '{"name": $name, "description": $desc, "customAttributes": []}')")
  GROUP_UUID=$(require_uuid "$_resp" "group '${GROUP_NAME}'")
  ok "group  $GROUP_UUID"
}

# --- Step 10: Issue certificate via EJBCA + ILM-managed key -------------------
issue_certificate() {
  local _resp csr_attrs cn_uuid sig_attrs sig_scheme_uuid sig_digest_uuid

  log "Fetching CSR attribute definitions..."
  csr_attrs=$(ilm_curl GET "/v1/certificates/csr/attributes")
  cn_uuid=$(attr_uuid "$csr_attrs" "commonName" "string")

  log "Fetching signature attribute definitions..."
  sig_attrs=$(ilm_curl GET \
    "/v1/operations/tokens/${TOKEN_UUID}/tokenProfiles/${TOKEN_PROFILE_UUID}/keys/${KEY_UUID}/items/${PRIVATE_KEY_ITEM_UUID}/signature/RSA/attributes")
  sig_scheme_uuid=$(attr_uuid "$sig_attrs" "data_rsaSigScheme" "string")
  sig_digest_uuid=$(attr_uuid "$sig_attrs" "data_sigDigest"    "string")

  log "Issuing certificate  CN=${CERTIFICATE_DN}..."
  _resp=$(ilm_curl POST \
    "/v2/operations/authorities/${AUTH_UUID}/raProfiles/${RA_PROFILE_UUID}/certificates" -d \
    "$(jq -n \
      --arg cn               "$CERTIFICATE_DN" \
      --arg keyUuid          "$KEY_UUID" \
      --arg tokenProfileUuid "$TOKEN_PROFILE_UUID" \
      --arg cnUuid           "$cn_uuid" \
      --arg sigSchemeUuid    "$sig_scheme_uuid" \
      --arg sigDigestUuid    "$sig_digest_uuid" \
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
            content: [{data: "SHA-256", reference: "SHA-256"}],
            contentType: "string",
            uuid: $sigDigestUuid,
            version: "v2"
          }
        ],
        keyUuid: $keyUuid,
        tokenProfileUuid: $tokenProfileUuid,
        customAttributes: []
      }')")
  ISSUED_CERT_UUID=$(require_uuid "$_resp" "certificate CN=${CERTIFICATE_DN}")
  ok "issued certificate  $ISSUED_CERT_UUID"
}

# --- Step 11: Poll for certificate issuance -----------------------------------
poll_certificate() {
  local cert_state="" cert_details attempt history err_msg err_text

  log "Waiting for certificate issuance to complete (CN=${CERTIFICATE_DN})..."
  for (( attempt=1; attempt<=CERT_POLL_ATTEMPTS; attempt++ )); do
    cert_details=$(ilm_curl GET "/v1/certificates/${ISSUED_CERT_UUID}")
    cert_state=$(echo "$cert_details" | jq -r '.state // empty')
    case "$cert_state" in
      issued)
        ok "certificate state: issued"
        break
        ;;
      failed)
        history=$(ilm_curl GET "/v1/certificates/${ISSUED_CERT_UUID}/history")
        err_msg=$(echo "$history" | jq -r '
          first(
            .[] | select(.event=="Issue Certificate" and .status=="FAILED") | .message
          ) // empty')
        if [[ -n "$err_msg" ]]; then
          err_text=$(echo "$err_msg" | jq -r '. | fromjson | .message' 2>/dev/null \
            || echo "$err_msg")
        else
          err_text="(no error message available in certificate history)"
        fi
        die "Certificate issuance failed: ${err_text}"
        ;;
      *)
        log "  attempt ${attempt}/${CERT_POLL_ATTEMPTS}: state='${cert_state}' -- waiting ${CERT_POLL_INTERVAL}s..."
        sleep "$CERT_POLL_INTERVAL"
        ;;
    esac
  done
  if [[ "$cert_state" != "issued" ]]; then
    die "Certificate issuance timed out after $(( CERT_POLL_ATTEMPTS * CERT_POLL_INTERVAL ))s (last state: '${cert_state}')"
  fi
}

# --- Step 12: Associate issued certificate with group -------------------------
# The RA profile association is set automatically during issuance.
# Key and CertificateRequest associations are set automatically when the CSR
# is signed with an ILM-managed key.
# Groups must be associated explicitly.
associate_issued_certificate() {
  log "Associating issued certificate with group '${GROUP_NAME}'..."
  ilm_curl PATCH "/v1/certificates/${ISSUED_CERT_UUID}" -d \
    "$(jq -n --arg g "$GROUP_UUID" '{"groupUuids": [$g]}')" >/dev/null
  ok "group associated with issued certificate"
}

# --- Step 13: Associate CA certificate with group ----------------------------
# The RA profile cannot be applied to the CA cert because the server validates
# that the certificate conforms to the RA profile's constraints.  Group
# association alone is sufficient to give the chain ancestor ILM entity
# coverage for chain retrieval tests.
associate_ca_certificate() {
  log "Associating CA certificate with group '${GROUP_NAME}'..."
  ilm_curl PATCH "/v1/certificates/${CA_CERT_UUID}" -d \
    "$(jq -n --arg g "$GROUP_UUID" '{"groupUuids": [$g]}')" >/dev/null
  ok "group associated with CA certificate"
}

# --- Summary ------------------------------------------------------------------
print_summary() {
  cat <<EOF

Setup complete. Created resources:

  connector      common-credential-provider      $CRED_CONN_UUID
  connector      ejbca-ng-connector              $EJBCA_CONN_UUID
  connector      software-cryptography-provider  $CRYPTO_CONN_UUID
  ca-cert        $(basename "$CA_PEM") (trusted) $CA_CERT_UUID
  credential     $CREDENTIAL_NAME                $CRED_UUID
  authority      $AUTHORITY_NAME                 $AUTH_UUID
  token          $TOKEN_NAME                     $TOKEN_UUID
  token-profile  $TOKEN_PROFILE_NAME             $TOKEN_PROFILE_UUID
  key            $KEY_NAME                       $KEY_UUID
  key-item       (private)                       $PRIVATE_KEY_ITEM_UUID
  ra-profile     $RA_PROFILE_NAME                $RA_PROFILE_UUID
  group          $GROUP_NAME                     $GROUP_UUID
  certificate    CN=$CERTIFICATE_DN              $ISSUED_CERT_UUID

Entity associations on issued certificate (CN=$CERTIFICATE_DN):
  ✓  RA Profile          $RA_PROFILE_UUID  (set during issuance)
  ✓  Group               $GROUP_UUID  ($GROUP_NAME)
  ✓  Key                 $KEY_UUID  ($KEY_NAME)
  ✓  Key Item            $PRIVATE_KEY_ITEM_UUID  (private key item)
  ✓  CertificateRequest  (CSR stored by ILM during issuance)

Entity associations on CA certificate  $CA_CERT_UUID:
  ✓  Group               $GROUP_UUID  ($GROUP_NAME)

Certificate chain in ILM DB (for chain retrieval):
  leaf:   $ISSUED_CERT_UUID  CN=$CERTIFICATE_DN
  issuer: $CA_CERT_UUID  (trusted CA, uploaded from $(basename "$CA_PEM"))

Verify chain retrieval:
  GET ${ILM_HOST}/api/v1/certificates/${ISSUED_CERT_UUID}/chain?withEndCertificate=true
EOF
}

# --- Main ---------------------------------------------------------------------
main() {
  parse_args "$@"
  validate
  setup_connectors
  upload_ca_cert
  setup_credential
  setup_authority
  setup_token
  setup_token_profile
  setup_key_pair
  setup_ra_profile
  create_group
  issue_certificate
  poll_certificate
  associate_issued_certificate
  associate_ca_certificate
  print_summary
}

main "$@"
