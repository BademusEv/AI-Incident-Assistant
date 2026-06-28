# AcmePay Production System

AcmePay is a fictional fintech payment platform.

Core services:

- `api-gateway` receives external HTTP requests from clients and routes them to internal services.
- `checkout-service` receives checkout requests and calls `payment-service`.
- `auth-service` handles authentication and token issuance.
- `payment-service` authorizes card payments through an external payment provider.
- `billing-service` manages balances and invoicing.
- `notification-service` sends customer receipts, e-mail, SMS, and merchant alerts through external providers.
- `reporting-service` builds merchant dashboards from the transactional PostgreSQL database.

Operational notes:

- All services write logs to centralized ELK log storage.
- PostgreSQL has separate instances for payment-service and billing-service.
- Payment failures are customer-facing and can block revenue.
- Notification failures usually do not block payments, but they affect customer trust and operational visibility.
- Reporting overload can degrade dashboards and database performance.
- Authentication failures can prevent customers or merchants from accessing the platform.
