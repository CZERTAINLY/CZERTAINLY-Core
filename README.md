# CZERTAINLY Core

> xxxxThis repository is part of the commercial open source project CZERTAINLY. You can find more information about the project at [CZERTAINLY](https://github.com/3KeyCompany/CZERTAINLY) repository, including the contribution guide.

`Core` provides the basic functionality for the CZERTAINLY platform. It implements the logic for the certificate lifecycle management and handles all related tasks. You can think about it as a brain of the CZERTAINLY platform.

There are 2 types of communication that the `Core` is responsible for:
- client requesting management operations on top of certificates
- `Connector` that provides with the functionality for specific technologies

`Core` is performing consistent operation on top of the certificates. The management of certificates is abstracted through CZERTAINLY objects, for example:

| Object               | Short description                                                                                                                                      |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Connector`          | Provides with the functionality for specific technologies (defined by `Function Group` and supported `Kinds`)                                          |
| `Credential`         | `Credential` of various types to be used by `Connectors` and other objects                                                                             |
| `Authority`          | Representing certification authority access                                                                                                            |
| `RA Profile`         | Configuration of the service for certificate lifecycle management (abstraction of `Attributes` for specific certificate type, including available APIs) |
| `Discovery`          | Schedule discovery process for searching of certificates in various sources                                                                            |
| `Certificate`        | `Certificate` consisting of `Attributes` and related metadata                                                                                          |
| `Entity`             | Represents the entity that is can use the certificates                                                                                                 |
| `Location`           | Location on the `Entity` where is certificate stored                                                                                                   |
| `Group`              | Grouping of different certificates based on different requirements                                                                                     |
| `Compliance Profile` | Matching rules for the certificate to assess compliance                                                                                                | 

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

`Core` is provided as a Docker container. Use the 3keycompany/czertainly-core:tagname to pull the required image from the repository. It can be configured using the following environment variables:

| Variable                | Description                                                         | Required | Default value     |
|-------------------------|---------------------------------------------------------------------|----------|-------------------|
| `JDBC_URL`              | JDBC URL for database access                                        | Yes      | N/A               |
| `JDBC_USERNAME`         | Username to access the database                                     | Yes      | N/A               |
| `JDBC_PASSWORD`         | Password to access the database                                     | Yes      | N/A               |
| `DB_SCHEMA`             | Database schema to use                                              | No       | ejbca             |
| `PORT`                  | Port where the service is exposed                                   | No       | 8080              |
| `HEADER_NAME`           | Name of the header where the certificate of the client can be found | No       | X-APP-CERTIFICATE |
| `HEADER_ENABLED`        | True if the certificate should be get from the header               | Yes      | N/A               |
| `TS_PASSWORD`           | Password for the trusted certificate store                          | Yes      | Any               |
| `OPA_BASE_URL`          | Base URL of the Open Policy Agent                                   | Yes      | N/A               |
| `AUTH_SERVICE_BASE_URL` | Base URL of the authentication service                              | Yes      | N/A               |

### Proxy settings

You may need to configure proxy to allow `Core` to communicate with external systems.
To enable proxy, use the following environment variables for docker container:

| Variable      | Description                                                                                                                                                | Required | Default value |
|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|---------------|
| `HTTP_PROXY`  | The proxy URL to use for http connections. Format: `<protocol>://<proxy_host>:<proxy_port>` or `<protocol>://<user>:<password>@<proxy_host>:<proxy_port>`  | No       | N/A           |
| `HTTPS_PROXY` | The proxy URL to use for https connections. Format: `<protocol>://<proxy_host>:<proxy_port>` or `<protocol>://<user>:<password>@<proxy_host>:<proxy_port>` | No       | N/A           |
| `NO_PROXY`    | A comma-separated list of host names that shouldn't go through any proxy                                                                                   | No       | N/A           |

Example values:
- `HTTP_PROXY=http://user:password@proxy.example.com:3128`
- `HTTPS_PROXY=http://user:password@proxy.example.com:3128`
- `NO_PROXY=localhost,127.0.0.1,0.0.0.0,10.0.0.0/8,cattle-system.svc,.svc,.cluster.local,my-domain.local`
