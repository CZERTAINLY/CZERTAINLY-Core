# CZERTAINLY Core

> This repository is part of the commercial open source project CZERTAINLY. You can find more information about the project at [CZERTAINLY](https://github.com/3KeyCompany/CZERTAINLY) repository, including the contribution guide.

`Core` provides the basic functionality for the CZERTAINLY platform. It implements the logic for the certificate lifecycle management and handles all related tasks. You can think about it as a brain of the CZERTAINLY platform.

There are 2 types of communication that the `Core` is responsible for:
- client requesting management operations on top of certificates
- `Connector` that provides with the functionality for specific technologies

`Core` is performing consistent operation on top of the certificates. The management of certificates is abstracted through CZERTAINLY objects, for example:

| Object               | Short description                                                                                                                                       |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Connector`          | Provides with the functionality for specific technologies (defined by `Function Group` and supported `Kinds`)                                           |
| `Credential`         | `Credential` of various types to be used by `Connectors` and other objects                                                                              |
| `Authority`          | Representing certification authority access                                                                                                             |
| `RA Profile`         | Configuration of the service for certificate lifecycle management (abstraction of `Attributes` for specific certificate type, including available APIs) |
| `Discovery`          | Schedule discovery process for searching of certificates in various sources                                                                             |
| `Certificate`        | `Certificate` consisting of `Attributes` and related metadata                                                                                           |
| `Entity`             | Represents the entity that is can use the certificates                                                                                                  |
| `Location`           | Location on the `Entity` where is certificate stored                                                                                                    |
| `Group`              | Grouping of different certificates based on different requirements                                                                                      |
| `Compliance Profile` | Matching rules for the certificate to assess compliance                                                                                                 | 

## Access Control

`Core` access control requires the following to run:
- [CZERTAINLY-Auth](https://github.com/3KeyCompany/CZERTAINLY-Auth) service to manage users, roles, permission. The URL of the `Auth` service can be configured using `AUTH_SERVICE_BASE_URL` environment variable.
- OPA (Open Policy Agent) evaluating policies and providing decisions about authorization. The OPA service URL can be confgiured using `OPA_BASE_URL` environment variable.
- OPA policies bundles that are loaded into OPA service and define the rules to be evaluated. The policies are defined in [CZERTAINLY-Auth-OPA-Policies](https://github.com/3KeyCompany/CZERTAINLY-Auth-OPA-Policies)

> **Warning**
> The `Core` will fail to run when `Auth` or OPA is missing.

> **Note**
> OPA can run on the same system with the `Core` or it can be hosted externally. To improve the performance of the permissions evaluation it is typically running on the same host as `Core` (e.g. as a sidecar).

## RA profile

`RA profile` is one of the main concepts of the `Core`. `RA profile` represents the certificate management service containing all specific `Attributes` and configuration you need for specific `Certificate` and use-case, which may be for example web server certificate, authentication certificates, etc.

Each `Certificate` type and use-case may have its own technical, business, and compliance requirements. `RA profile` is the abstraction of these requirements.

The relation between `RA profile` and other objects may be defined according the following scheme:
```
Authority----------------\
                          \
Certificate attributes----\\           /----certificate
                           \\         /
Compliance profile---------\\\       //-----certificate
                            RA profile
Approval profile-----------///       \\-----certificate
                           //         \
Interfaces----------------//           \----certificate
                          /
Authorization------------/
```

For more information, refer to the [CZERTAINLY documentation](https://docs.czertainly.com).

## Certificate inventory

`Certificate` inventory contains all `Certificates` that were discovered or were imported to the platform. Each `Certificate` provides comprehensive and consistent information which can be managed.

### Lifecycle operations

The following basic lifecycle operations are supported for each `Certificate`:
- issue
- renew
- revoke

Operations can be automated by the `Core`, but also can be performed manually by the user.

### Certificate relations

`Certificate` has relations to other objects that helps with the management and automation of the `Certificate` lifecycle:

```
Location----------\
                   \
Group--------------\\
                    certificate-------RA profile
Discovery----------//
                   /
Owner-------------/
```

For more information, refer to the [CZERTAINLY documentation](https://docs.czertainly.com).

## Protocol support

`Core` support the following protocols for certificate management:
- ACME

## Docker container

`Core` is provided as a Docker container. Use the `3keycompany/czertainly-core:tagname` to pull the required image from the repository. It can be configured using the following environment variables:

| Variable                 | Description                                                         | Required                                           | Default value       |
|--------------------------|---------------------------------------------------------------------|----------------------------------------------------|---------------------|
| `JDBC_URL`               | JDBC URL for database access                                        | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`               |
| `JDBC_USERNAME`          | Username to access the database                                     | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`               |
| `JDBC_PASSWORD`          | Password to access the database                                     | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`               |
| `DB_SCHEMA`              | Database schema to use                                              | ![NO](https://img.shields.io/badge/-NO-red.svg)    | core                |
| `PORT`                   | Port where the service is exposed                                   | ![NO](https://img.shields.io/badge/-NO-red.svg)    | `8080`              |
| `HEADER_NAME`            | Name of the header where the certificate of the client can be found | ![NO](https://img.shields.io/badge/-NO-red.svg)    | `X-APP-CERTIFICATE` |
| `HEADER_ENABLED`         | True if the certificate should be get from the header               | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`               |
| `TS_PASSWORD`            | Password for the trusted certificate store                          | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`               |
| `OPA_BASE_URL`           | Base URL of the Open Policy Agent                                   | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`               |
| `AUTH_SERVICE_BASE_URL`  | Base URL of the authentication service                              | ![](https://img.shields.io/badge/-YES-success.svg) | `N/A`               |
| `AUTH_TOKEN_HEADER_NAME` | Name of the header for the JSON ID content                          | ![NO](https://img.shields.io/badge/-NO-red.svg)    | `X-USERINFO`        |
| `AUDITLOG_ENABLED`       | Audit log enable / disable                                          | ![NO](https://img.shields.io/badge/-NO-red.svg)    | `false`             |
| `SCHEDULED_TASKS_ENABLED`| Scheduled certificate status update enable / disable                | ![NO](https://img.shields.io/badge/-NO-red.svg)    | `true`              |
| `JAVA_OPTS`              | Customize Java system properties for running application            | ![NO](https://img.shields.io/badge/-NO-red.svg)    | `N/A`               |
| `TRUSTED_CERTIFICATES`   | List of PEM encoded additional trusted certificates                 | ![](https://img.shields.io/badge/-NO-red.svg)      | `N/A`               |

### Proxy settings

You may need to configure proxy to allow `Core` to communicate with external systems.
To enable proxy, use the following environment variables for docker container:

| Variable      | Description                                                                                                                                                | Required                                        | Default value |
|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------|---------------|
| `HTTP_PROXY`  | The proxy URL to use for http connections. Format: `<protocol>://<proxy_host>:<proxy_port>` or `<protocol>://<user>:<password>@<proxy_host>:<proxy_port>`  | ![NO](https://img.shields.io/badge/-NO-red.svg) | `N/A`         |
| `HTTPS_PROXY` | The proxy URL to use for https connections. Format: `<protocol>://<proxy_host>:<proxy_port>` or `<protocol>://<user>:<password>@<proxy_host>:<proxy_port>` | ![NO](https://img.shields.io/badge/-NO-red.svg) | `N/A`         |
| `NO_PROXY`    | A comma-separated list of host names that shouldn't go through any proxy                                                                                   | ![NO](https://img.shields.io/badge/-NO-red.svg) | `N/A`         |

Example values:
- `HTTP_PROXY=http://user:password@proxy.example.com:3128`
- `HTTPS_PROXY=http://user:password@proxy.example.com:3128`
- `NO_PROXY=localhost,127.0.0.1,0.0.0.0,10.0.0.0/8,cattle-system.svc,.svc,.cluster.local,my-domain.local`
