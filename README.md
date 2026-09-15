# ms-barriodigital-bff

BFF de BarrioDigital para recibir llamadas del frontend React, validar access tokens JWT emitidos por Microsoft Entra ID y reenviar las solicitudes a los microservicios internos correspondientes.

## Arquitectura

```text
React + MSAL
  -> Authorization: Bearer <access_token>
ms-barriodigital-bff
  -> requests-service / catalog-service / report-service / audit-service
```

Más adelante el flujo puede incorporar AWS API Gateway delante del BFF sin cambiar el contrato actual del frontend.

## Stack

- Java 21
- Spring Boot 3.x
- Maven
- Spring Web
- Spring Security
- OAuth2 Resource Server JWT
- Validation
- Actuator health

Este BFF no usa base de datos, JPA, Hibernate, entidades ni drivers. La persistencia vive en los microservicios de dominio.

## Variables de entorno

No se incluyen IDs reales ni secretos. El BFF no utiliza Client Secret.

```env
AZURE_TENANT_ID=
AZURE_API_AUDIENCE=api://API_CLIENT_ID
FRONTEND_ORIGIN=http://localhost:5173
REQUESTS_SERVICE_URL=http://localhost:8081
CATALOG_SERVICE_URL=http://localhost:8082
REPORT_SERVICE_URL=http://localhost:8083
AUDIT_SERVICE_URL=http://localhost:8084
```

Ejemplo PowerShell:

```powershell
$env:AZURE_TENANT_ID="..."
$env:AZURE_API_AUDIENCE="api://..."
$env:FRONTEND_ORIGIN="http://localhost:5173"
$env:REQUESTS_SERVICE_URL="http://localhost:8081"
$env:CATALOG_SERVICE_URL="http://localhost:8082"
$env:REPORT_SERVICE_URL="http://localhost:8083"
$env:AUDIT_SERVICE_URL="http://localhost:8084"
```

## Ejecución

Desde la carpeta Maven:

```powershell
cd .\barriodigitalbff
.\mvnw.cmd spring-boot:run
```

El servicio escucha en:

```text
http://localhost:8080
```

## JWT y seguridad

El BFF funciona como OAuth2 Resource Server. Valida:

- firma del JWT usando JWK de Microsoft Entra ID;
- expiración y vigencia;
- issuer `https://login.microsoftonline.com/${AZURE_TENANT_ID}/v2.0`;
- audience configurado en `AZURE_API_AUDIENCE`;
- scope `access_as_user`;
- roles `Admin`, `Operador`, `Cliente`, `Auditor`.

Los roles de Azure se convierten así internamente:

- `Admin` -> `ROLE_Admin`
- `Operador` -> `ROLE_Operador`
- `Cliente` -> `ROLE_Cliente`
- `Auditor` -> `ROLE_Auditor`

El scope se convierte con el estándar Spring:

- `access_as_user` -> `SCOPE_access_as_user`

## Endpoints BFF

### Público

```text
GET /api/bff/health
GET /actuator/health
```

Ejemplo:

```powershell
Invoke-RestMethod http://localhost:8080/api/bff/health
```

Respuesta:

```json
{
  "status": "UP",
  "service": "ms-barriodigital-bff"
}
```

### Autenticado

```text
GET /api/bff/me
```

Requiere JWT válido y `SCOPE_access_as_user`.

Ejemplo:

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8080/api/bff/me `
  -Headers @{ Authorization = "Bearer <access_token>" }
```

Devuelve información segura del token: subject, name, username, roles, scopes, issuer, audience y expiresAt. No devuelve access token, refresh token ni secretos.

## Proxy hacia microservicios

El BFF preserva método HTTP, path, query parameters, body JSON, Content-Type, status HTTP y body de respuesta. También reenvía el Bearer token original.

```text
/api/requests/** -> REQUESTS_SERVICE_URL
/api/catalog/**  -> CATALOG_SERVICE_URL
/api/report/**   -> REPORT_SERVICE_URL
/api/audit/**    -> AUDIT_SERVICE_URL
```

Si un microservicio no está disponible, responde 503 con JSON legible para el frontend.

## Rutas esperadas por el frontend

### Trámites

```text
GET  /api/requests
GET  /api/requests/{id}
POST /api/requests
PUT  /api/requests/{id}/status
```

### Catálogo

```text
GET  /api/catalog/procedures
POST /api/catalog/procedures
PUT  /api/catalog/procedures/{id}
```

### Reportería

```text
GET /api/report/kpis?range=last24h
GET /api/report/top-procedures?range=last7d
```

### Auditoría

```text
GET /api/audit
GET /api/audit?user=test&date=2026-09-11&eventType=REQUEST_STATUS_CHANGED
```

## Matriz de autorización

Todas las rutas de negocio requieren `SCOPE_access_as_user` más rol válido.

| Ruta | Roles |
| --- | --- |
| `GET /api/requests/**` | Admin, Operador, Cliente |
| `POST /api/requests/**` | Admin, Operador, Cliente |
| `PUT /api/requests/{id}/status` | Admin, Operador |
| `GET /api/catalog/**` | Admin, Operador |
| `POST /api/catalog/**` | Admin |
| `PUT /api/catalog/**` | Admin |
| `GET /api/report/**` | Admin |
| `GET /api/audit/**` | Admin, Auditor |

Auditoría es solo lectura. Otros métodos sobre `/api/audit/**` son denegados.

## Errores JSON

El BFF responde JSON, no HTML, para errores relevantes:

- `401 Unauthorized`: falta token o token inválido.
- `403 Forbidden`: token válido sin rol/scope suficiente.
- `404 Not Found`: recurso inexistente.
- `500 Internal Server Error`: error inesperado.
- `503 Service Unavailable`: microservicio downstream no disponible.

Formato:

```json
{
  "timestamp": "...",
  "status": 503,
  "error": "Service Unavailable",
  "message": "El servicio de trámites no está disponible.",
  "path": "/api/requests"
}
```

## Tests y build

```powershell
cd .\barriodigitalbff
.\mvnw.cmd test
.\mvnw.cmd clean package
```

Los tests usan JWT mockeado solo en ambiente de pruebas. No dependen de Microsoft Entra real.

## Pendiente

Queda pendiente crear y ejecutar los microservicios reales:

- requests-service
- catalog-service
- report-service
- audit-service

Cuando existan, basta configurar sus URLs con las variables de entorno correspondientes.
