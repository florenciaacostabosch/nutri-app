# nutri-app

Plataforma para nutricionistas y pacientes: reserva de turnos con disponibilidad, seguimiento del progreso (peso, medidas, objetivos), notas de consulta y planes alimentarios con seguimiento de cumplimiento.

## Stack

- **Backend:** Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Maven
- **Base de datos:** PostgreSQL 16, migraciones con Flyway
- **Frontend:** React, TypeScript, Vite
- **Infraestructura:** Docker, Docker Compose

## Estructura

```
nutri-app/
├── backend/             # API REST (Spring Boot)
├── frontend/            # Aplicación web (React + Vite)
└── docker-compose.yml   # PostgreSQL para desarrollo
```

## Requisitos

- Java 21
- Node.js 20 o superior
- Docker con Docker Compose

## Ejecutar en local

**1. Base de datos**

```bash
docker compose up -d
```

**2. Backend** (en otra terminal). Al arrancar, Flyway aplica las migraciones.

```bash
cd backend
./mvnw spring-boot:run
```

**3. Frontend** (en otra terminal)

```bash
cd frontend
npm install
npm run dev
```

Abrir http://localhost:5173. La página debe mostrar `Backend: UP`.

Para comprobar solo el backend:

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}
```

## Configuración

El backend usa por defecto la base de datos del `docker-compose.yml`. Se puede cambiar con las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD`.

## Estado del proyecto

- [x] Base e infraestructura: Spring Boot, PostgreSQL, Flyway, Docker Compose y React
- [ ] Autenticación con JWT y roles
- [ ] Turnos y disponibilidad
- [ ] Seguimiento del progreso
- [ ] Planes alimentarios
- [ ] CI con GitHub Actions, documentación de la API y despliegue
