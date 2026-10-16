# BFF en AWS

El proyecto `barriodigitalbff/` ya usa variables y Docker no-root con Java 21 /
Spring Boot 3.3.6. No se cambia codigo Java, rutas, JWT ni reglas de autorizacion.

- `AZURE_TENANT_ID`: tenant existente; issuer Microsoft `https://login.microsoftonline.com/<tenant>/v2.0`.
- `AZURE_API_AUDIENCE`: audience EXACTA del access token emitido para la API,
  segun su configuracion actual. No asumir que siempre sera api://client-id.
- Se conservan firma, issuer, audience, vigencia, roles Admin/Operador/Cliente/Auditor
  y scope access_as_user. No agregar client secret ni un login backend.
- `FRONTEND_ORIGIN`: origen HTTPS de Amplify sin ruta/slash final. Local conserva
  http://localhost:5173. No usar comodin ni reemplazar el login.
- `REQUESTS_SERVICE_URL=http://requests:8081`, `CATALOG_SERVICE_URL=http://catalog:8082`
  se inyectan en el Compose AWS, nunca localhost dentro de contenedores distintos.
- `REPORT_SERVICE_URL`, `AUDIT_SERVICE_URL` siguen configurables. Estos servicios
  no se crean en esta fase; las rutas existentes responden 503 si no estan disponibles.

El Compose central de infra publica solo el BFF en `BFF_BIND_ADDRESS:BFF_PORT`.
Por seguridad el default es loopback. Antes de desplegar verificar API Gateway:
la integracion anterior probablemente es HTTP publico hacia la Elastic IP de apps,
pero no esta confirmada. Para ese caso enlazar la IP PRIVADA de la EC2 (NAT publica)
o 0.0.0.0 con SG restrictivo; no enlazar una Elastic IP que no existe en la interfaz.
Mantener endpoint/stage de API Gateway configurado en Amplify. No crear VPC Link/ALB.
El TLS del navegador termina en API Gateway; evaluar manualmente el tramo HTTP
Gateway -> EC2 y las reglas de acceso actuales antes de habilitarlo.

No se expone RabbitMQ Admin a traves del BFF: falta un contrato obligatorio de
rutas administrativas. La API interna mantiene Basic + X-Admin-Request; no se
envian credenciales administrativas al navegador ni se inventan permisos.

```powershell
cd barriodigitalbff
.\mvnw.cmd -B -ntp clean package
```

Salud publica: `/api/bff/health` o `/actuator/health`. `/api/bff/me` exige Bearer
valido y scope access_as_user. Guia: README.AWS.md de infra-barriodigital.
