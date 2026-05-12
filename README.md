# ESILigue

Guia rapida para levantar backend + base de datos y ejecutar la app Android.

## 1) Requisitos

- Docker Desktop (con Docker Compose)
- Android Studio (version reciente)
- JDK 21 (recomendado para backend si lo ejecutas fuera de Docker)
- Nota: Nosotros para la interfaz de la Base de Datos hemos usado DBeaver

### Pasos a seguir para ver la BD en la interfaz.

Para replicar el entorno de desarrollo, se debe configurar la conexión en el gestor de base de datos (DBeaver/SQL Developer) con los siguientes parámetros:
- Motor de BD: Oracle Database (Express Edition).
- Host: localhost (Servidor local).
- Puerto: 1522
- Servicio (PDB): XEPDB1
- Usuario: esiligue_admin
- Contraseña: uca_password
- Rol: Normal
- Driver: Oracle JDBC Thin (no requiere cliente local instalado).

**Nota** : Asegúrese de que el listener de Oracle esté escuchando en el puerto 1522 y que la base de datos pluggable XEPDB1 esté en modo READ WRITE antes de intentar la conexión.

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

Por defecto (si no defines nada) la app usa:

```properties
http://10.0.2.2:8080/
```

Ese valor funciona directamente en emulador Android y evita meter IPs personales en el repo.

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

Recomendacion para entregar al profesor sin friccion:

- Probar con emulador Android (no requiere cambiar IP).
- No versionar una IP fija en `gradle.properties`.

## 4) Ejecutar app Android

1. Abre la carpeta `frontend-app` en Android Studio.
2. Espera a que sincronice Gradle.
3. Preferencia recomendada para pruebas del profesor: usar un emulador Android (AVD).
	- El emulador usa la dirección especial `10.0.2.2` que ya está configurada por defecto en la app.
	- No hace falta editar `gradle.properties` ni tocar la IP local.
4. Alternativa rápida (CLI): usa el script helper incluido para arrancar backend, compilar e instalar en un emulador ya iniciado.

```powershell
# Desde la raíz del repo en Windows PowerShell
\frontend-app\scripts\run_on_emulator.ps1
```

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
