# ms-barriodigital-bff

Backend for Frontend de BarrioDigital. Es la entrada de aplicación después de AWS API Gateway: valida nuevamente el JWT emitido por Microsoft Entra ID, aplica autorización por scope y rol, obtiene la identidad del usuario y deriva la solicitud al microservicio interno correspondiente.

## Stack

- Java 21
- Spring Boot 3.3.6
- Spring Web
- Spring Security
- OAuth2 Resource Server JWT
- Actuator
- Maven

El BFF no tiene base de datos propia.

## Flujo

```text
Frontend React + MSAL
        |
        | Bearer JWT
        v
AWS API Gateway
  JWT Authorizer
        |
        v
ms-barriodigital-bff :8080
  Spring Security
        |
        +--> requests :8081
        +--> catalog  :8082
```

API Gateway realiza una primera validación del token. El BFF vuelve a validar firma, issuer, vigencia y audience y luego revisa scope y roles antes de permitir el acceso a una ruta.

## Variables de entorno

Crear `.env` dentro de `barriodigitalbff/` a partir de `.env.example`:

```env
SERVER_PORT=8080
AZURE_TENANT_ID=YOUR_TENANT_ID
AZURE_API_AUDIENCE=YOUR_API_CLIENT_ID
FRONTEND_ORIGIN=http://localhost:5173
REQUESTS_SERVICE_URL=http://localhost:8081
CATALOG_SERVICE_URL=http://localhost:8082
```

En Docker Compose las URLs internas se sobrescriben con los nombres DNS de los servicios:

```text
http://requests:8081
http://catalog:8082
```

`AZURE_API_AUDIENCE` debe coincidir con el claim `aud` del access token real.

## Validación JWT

El BFF valida:

- firma usando las claves públicas de Microsoft;
- issuer del tenant;
- expiración y vigencia;
- audience esperada;
- scope `access_as_user`;
- roles de aplicación.

Roles reconocidos:

- `Admin`
- `Operador`
- `Cliente`
- `Auditor`

Los claims `roles` se convierten a authorities de Spring con prefijo `ROLE_`, y el scope se convierte a `SCOPE_access_as_user`.

## Matriz de autorización

| Operación | Admin | Operador | Cliente | Auditor |
| --- | :---: | :---: | :---: | :---: |
| `GET /api/bff/me` | Sí | Sí | Sí | Sí |
| `GET /api/requests/**` | Sí | Sí | Sí | No |
| `POST /api/requests/**` | Sí | Sí | Sí | No |
| `PUT /api/requests/{id}/status` | Sí | Sí | No | No |
| `GET /api/catalog/**` | Sí | Sí | Sí | No |
| `POST /api/catalog/**` | Sí | No | No | No |
| `PUT /api/catalog/**` | Sí | No | No | No |
| `GET /api/report/**` | Sí | No | No | No |
| `GET /api/audit/**` | Sí | No | No | Sí |

Todas las rutas de negocio requieren además `SCOPE_access_as_user`.

## Identidad hacia los microservicios

El BFF no confía en headers de identidad enviados por el navegador. Antes de reenviar la petición elimina valores externos de:

```text
Authorization
X-User-Email
X-User-Id
X-User-Roles
```

Luego reconstruye la identidad a partir del JWT ya validado y envía internamente:

```text
X-User-Email
X-User-Id
X-User-Roles
```

Esto permite que Requests aplique reglas como “Cliente solo puede leer sus propios trámites” sin aceptar una identidad manipulada desde el frontend.

## Endpoints propios

### Públicos

```text
GET /api/bff/health
GET /actuator/health
```

### Autenticado

```text
GET /api/bff/me
```

`/api/bff/me` devuelve información del JWT validado, como usuario, roles, scopes, issuer, audience y expiración. No devuelve el token.

## Rutas proxy

```text
/api/requests/** -> REQUESTS_SERVICE_URL
/api/catalog/**  -> CATALOG_SERVICE_URL
/api/report/**   -> REPORT_SERVICE_URL
/api/audit/**    -> AUDIT_SERVICE_URL
```

En la entrega actual, Docker Compose levanta BFF, Requests y Catalog. Las rutas de Report y Audit quedan definidas en el BFF para una integración posterior, pero esos dos servicios no forman parte del Compose actual.

## Errores

- `401`: token ausente o inválido.
- `403`: token válido sin scope o rol suficiente.
- `404`: recurso inexistente devuelto por el servicio de dominio.
- `503`: microservicio downstream no disponible.

Los errores del BFF se devuelven en JSON.

## Ejecución local

```powershell
cd .\barriodigitalbff
.\mvnw.cmd spring-boot:run
```

BFF: `http://localhost:8080`

## Tests

```powershell
cd .\barriodigitalbff
.\mvnw.cmd test
.\mvnw.cmd clean package
```

Las pruebas incluidas cubren autenticación, autorización por rol, validación de audience y propagación segura de identidad hacia los servicios internos.
