# ESILigue

Guia rapida para levantar backend + base de datos y ejecutar la app Android.

## 1) Requisitos

- Docker Desktop (con Docker Compose)
- Android Studio (version reciente)
- JDK 21 (recomendado para backend si lo ejecutas fuera de Docker)
- Misma red Wi-Fi en PC y movil si vas a probar en telefono fisico

## 2) Levantar backend y base de datos (Docker)

Desde la raiz del repo:

```bash
docker compose up -d
```

Comprobar estado:

```bash
docker compose ps
```

Ver logs (si algo falla):

```bash
docker compose logs -f oracle-db
docker compose logs -f api
```

La API queda publicada en el puerto `8080` de tu PC.

## 3) Configurar URL de API en Android

La app usa la propiedad Gradle `API_BASE_URL`.

Archivo: `frontend-app/gradle.properties`

### Emulador Android

Usa:

```properties
API_BASE_URL=http://10.0.2.2:8080/
```

### Movil Android fisico

Usa la IP local del PC (ejemplo):

```properties
API_BASE_URL=http://192.168.1.34:8080/
```

Notas importantes para movil fisico:

- El movil y el PC deben estar en la misma red.
- El firewall de Windows debe permitir conexiones entrantes al puerto `8080`.
- Si cambia la IP del PC, actualiza `API_BASE_URL`.

## 4) Ejecutar app Android

1. Abre la carpeta `frontend-app` en Android Studio.
2. Espera a que sincronice Gradle.
3. Selecciona un emulador o tu movil conectado.
4. Pulsa Run para instalar y ejecutar.

## 5) Flujo de uso recomendado

1. Levanta Docker (`oracle-db` + `api`).
2. Arranca la app Android.
3. Registra usuario desde la app.
4. Inicia sesion y prueba discovery/swipes/matches.

## 6) Solucion de problemas

- Error de red en Android:
	- Revisa que `API_BASE_URL` sea correcta.
	- Comprueba que `docker compose ps` muestra `api` y `oracle-db` en estado healthy/running.
- El movil no conecta pero el emulador si:
	- Suele ser red/firewall/IP local incorrecta.
- La API no arranca:
	- Revisa logs de `oracle-db` primero y despues `api`.