# CZERTAINLY Core

> This repository is part of the commercial open source project CZERTAINLY. You can find more information about the project at [CZERTAINLY](https://github.com/3KeyCompany/CZERTAINLY) repository, including the contribution guide.

`Core` provides the basic functionality for the CZERTAINLY platform. It implements the logic for the certificate lifecycle management and handles all related tasks. You can think about it as a brain of the CZERTAINLY platform.

There are 2 types of communication that the `Core` is responsible for:
- client requesting management operations on top of certificates
- `Connector` that provides with the functionality for specific technologies

`Core` is performing consistent operation on top of the certificates. The management of certificates is abstracted through CZERTAINLY objects, for example:

| Object | Short description |
| ---------------- | ----------- |
| `Connector` | Provides with the functionality for specific technologies (defined by `Function Group` and supported `Kinds`) |
| `Credential` | `Credential` of various types to be used by `Connectors` and other objects |
| `Authority` | Representing certification authority access |
| `RA profile` | Configuration of the service for certificate lifecycle management (abstraction of `Attributes` for specific certificate type, including available APIs) |
| `Discovery` | Schedule discovery process for searching of certificates in various sources |
| `Certificate` | `Certificate` consisting of `Attributes` and related metadata |
| `Entity` | Represents the entity that is going to use the certificates |
| `Group` | Grouping of different certificates based on different requirements |

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
Entity------------\
                   \
Group--------------\\
                    certificate-------RA profile
Discovery----------//
                   /
Owner-------------/
```

For more information, refer to the [CZERTAINLY documentation](https://docs.czertainly.com).

## Monitoring and reporting

For monitoring and reporting, you can use the information provided by the `Core`. We strongly recommend trying the Operator UI that is additional component of the CZERTAINLY platform.