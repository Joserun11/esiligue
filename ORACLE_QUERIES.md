# Guía para Consultar las Tablas de la BD Oracle

## Acceso a la Base de Datos desde PowerShell

### 1. Conectar al contenedor Oracle

```powershell
# Ver el ID del contenedor
docker ps

# Acceder al contenedor Oracle
docker exec -it oracle_esiligue sqlplus esiligue_admin/uca_password@localhost:1522/XEPDB1
```

Si estás en la carpeta del proyecto, puedes usar:
```powershell
docker compose exec oracle sqlplus esiligue_admin/uca_password@localhost:1522/XEPDB1
```

### 2. Una vez dentro de SQLPlus, verifica las tablas disponibles:

```sql
-- Ver todas las tablas del esquema
SELECT table_name FROM user_tables;

-- Ver estructura de una tabla específica
DESC USUARIO;
DESC SWIPE;
DESC MATCH;
DESC PreferenciaBusqueda;
```

## Consultas Útiles para Verificar Datos

### A. Ver todos los usuarios registrados

```sql
SELECT u.id_usuario, 
       u.datos.nombre AS nombre, 
       u.datos.correo AS correo,
       u.datos.genero AS genero,
       u.datos.ubicacion.ciudad AS ciudad
FROM ESILIGUE_ADMIN."USUARIO" u;
```

### B. Ver todos los SWIPES (Likes, Super Likes, y Passes)

```sql
SELECT s.id_swipe, 
       s.id_origen, 
       s.id_destino, 
       s.tipo_swipe,
       s.fecha_swipe
FROM ESILIGUE_ADMIN.SWIPE s
ORDER BY s.fecha_swipe DESC;
```

### C. Ver solo los LIKES recibidos por un usuario

```sql
-- Cambiar 1 por el ID del usuario que quieras consultar
SELECT s.id_origen, 
       s.id_destino, 
       s.tipo_swipe,
       s.fecha_swipe
FROM ESILIGUE_ADMIN.SWIPE s
WHERE s.id_destino = 1 
  AND s.tipo_swipe = 'LIKE'
ORDER BY s.fecha_swipe DESC;
```

### D. Ver los MATCHES confirmados

```sql
SELECT m.id_match, 
       m.id_usuario1, 
       m.id_usuario2, 
       m.fecha_match
FROM ESILIGUE_ADMIN.MATCH m
ORDER BY m.fecha_match DESC;
```

### E. Ver preferencias de búsqueda de un usuario

```sql
-- Cambiar 1 por el ID del usuario
SELECT id_usuario,
       edad_min,
       edad_max,
       genero_interes,
       ciudad_interes,
       distancia_maxima
FROM ESILIGUE_ADMIN.PreferenciaBusqueda
WHERE id_usuario = 1;
```

### F. Contar total de usuarios, swipes y matches

```sql
SELECT 
    (SELECT COUNT(*) FROM ESILIGUE_ADMIN."USUARIO") AS total_usuarios,
    (SELECT COUNT(*) FROM ESILIGUE_ADMIN.SWIPE) AS total_swipes,
    (SELECT COUNT(*) FROM ESILIGUE_ADMIN.MATCH) AS total_matches
FROM DUAL;
```

### G. Ver el historial completo de un usuario (Quién le dio like, quién matcheó con él, etc.)

```sql
-- Cambiar 1 por el ID del usuario
SELECT 'LIKE RECIBIDO' AS tipo,
       s.id_origen AS usuario_otro,
       u.datos.nombre AS nombre_otro
FROM ESILIGUE_ADMIN.SWIPE s
JOIN ESILIGUE_ADMIN."USUARIO" u ON s.id_origen = u.id_usuario
WHERE s.id_destino = 1 AND s.tipo_swipe = 'LIKE'

UNION ALL

SELECT 'LIKE ENVIADO' AS tipo,
       s.id_destino AS usuario_otro,
       u.datos.nombre AS nombre_otro
FROM ESILIGUE_ADMIN.SWIPE s
JOIN ESILIGUE_ADMIN."USUARIO" u ON s.id_destino = u.id_usuario
WHERE s.id_origen = 1 AND s.tipo_swipe = 'LIKE'

UNION ALL

SELECT 'MATCH' AS tipo,
       CASE WHEN m.id_usuario1 = 1 THEN m.id_usuario2 ELSE m.id_usuario1 END AS usuario_otro,
       u.datos.nombre AS nombre_otro
FROM ESILIGUE_ADMIN.MATCH m
JOIN ESILIGUE_ADMIN."USUARIO" u ON (CASE WHEN m.id_usuario1 = 1 THEN m.id_usuario2 ELSE m.id_usuario1 END) = u.id_usuario
WHERE m.id_usuario1 = 1 OR m.id_usuario2 = 1
ORDER BY tipo;
```

## Alternativa: Usar DBeaver (GUI)

Si prefieres una interfaz gráfica:

1. Descarga DBeaver (gratis): https://dbeaver.io/download/
2. Crea una nueva conexión:
   - Driver: Oracle
   - Host: `localhost` o `127.0.0.1`
   - Puerto: `1522`
   - Database: `XEPDB1`
   - Usuario: `esiligue_admin`
   - Contraseña: `uca_password`
3. Conéctate y explora las tablas con navegador visual

## Salir de SQLPlus

```sql
EXIT;
-- O
QUIT;
```

## Notas Importantes

- El esquema es `ESILIGUE_ADMIN`
- Los datos de usuario están dentro de la columna `datos` (tipo objeto TipoUsuarioFree)
- Para acceder a campos anidados usa: `tabla.columna_objeto.campo`
- Ejemplo: `u.datos.nombre`, `u.datos.ubicacion.ciudad`
- Los tipos de SWIPE son: 'LIKE', 'SUPERLIKE', 'PASS' (o 'LEFT'/'RIGHT' según cómo los hayas implementado)

## Ejemplo PowerShell Completo (Todo en una línea)

```powershell
# Conéctate, ejecuta una query y sal
docker compose exec -T oracle sqlplus -S esiligue_admin/uca_password@localhost:1522/XEPDB1 @<<EOF
SELECT COUNT(*) FROM ESILIGUE_ADMIN."USUARIO";
EXIT;
EOF
```
