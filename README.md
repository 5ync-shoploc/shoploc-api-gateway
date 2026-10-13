# Shoploc API Gateway

## Overview

`shoploc-api-gateway` is the single entry point for the Shoploc microservices
platform.

It receives incoming client requests and routes them to the appropriate
backend microservice.

The API Gateway centralizes cross-cutting concerns at the platform entry
point, such as request routing, authentication, and access control.

## Responsibilities

The API Gateway is responsible for:

- Providing a single entry point to the Shoploc platform
- Routing requests to the appropriate microservice
- Validating authentication tokens
- Applying access-control rules
- Managing cross-cutting gateway concerns
- Hiding internal microservice endpoints from external clients

The Gateway does not contain business logic belonging to individual
microservices.

## Architecture

```text
                         Clients
                            │
                            ▼
                  ┌─────────────────────┐
                  │   Shoploc API       │
                  │      Gateway        │
                  └──────────┬──────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
        ┌───────────┐  ┌────────────┐  ┌────────────┐
        │ Identity  │  │ Governance │  │  Catalog   │
        │ Service   │  │  Service   │  │  Service   │
        └───────────┘  └────────────┘  └────────────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
                    Other Shoploc Services