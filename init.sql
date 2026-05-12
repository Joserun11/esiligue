-- ==========================================
-- CAMBIO DE SESIÓN AL USUARIO DE LA APP
-- El init.sql corre como SYS por defecto.
-- Los triggers y objetos deben crearse como esiligue_admin.
-- ==========================================
ALTER SESSION SET CONTAINER = XEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = esiligue_admin;

-- ==========================================
-- 1. TIPOS DE DATOS (Objetos)
-- Orden correcto: TipoListaFotos PRIMERO
-- ==========================================

CREATE OR REPLACE TYPE TipoUbicacion AS OBJECT (
    calle VARCHAR2(50),
    ciudad VARCHAR2(50),
    latitud NUMBER(10, 6),
    longitud NUMBER(10, 6)
);
/

-- FIX: TipoListaFotos se define ANTES de TipoUsuario (que lo referencia)
CREATE OR REPLACE TYPE TipoListaFotos AS TABLE OF NUMBER;
/

CREATE OR REPLACE TYPE TipoUsuario AS OBJECT (
    nombre VARCHAR2(50),
    correo VARCHAR2(50),
    contrasena VARCHAR2(255),
    fecha_nacimiento DATE,
    carrera VARCHAR2(100),
    descripcion VARCHAR2(500),
    genero CHAR(1),
    es_premium CHAR(1),
    ubicacion TipoUbicacion,
    fotos TipoListaFotos  -- FIX: coma añadida en la línea anterior
) NOT FINAL;
/

CREATE OR REPLACE TYPE TipoUsuarioPremium UNDER TipoUsuario(
    fecha_inicio DATE,
    fecha_fin DATE
);
/

CREATE OR REPLACE TYPE TipoUsuarioFree UNDER TipoUsuario(
    numero_swipes_disponibles NUMBER
);
/

-- ==========================================
-- 2. SECUENCIAS (antes de las tablas)
-- ==========================================

CREATE SEQUENCE id_match
INCREMENT BY 1
START WITH 1;

CREATE SEQUENCE id_usuario
INCREMENT BY 1
START WITH 1;

CREATE SEQUENCE id_archivo
INCREMENT BY 1
START WITH 1;

CREATE SEQUENCE id_mensaje
INCREMENT BY 1
START WITH 1;

CREATE SEQUENCE id_swipe
INCREMENT BY 1
START WITH 1;


-- ==========================================
-- 3. TABLAS
-- ==========================================

CREATE TABLE Usuario (
    id_usuario NUMBER PRIMARY KEY,
    datos TipoUsuario,
    CONSTRAINT chk_nombre_nn CHECK (datos.nombre IS NOT NULL),
    CONSTRAINT chk_correo_nn CHECK (datos.correo IS NOT NULL),
    CONSTRAINT chk_fecha_nac_nn CHECK (datos.fecha_nacimiento IS NOT NULL),
    CONSTRAINT check_genero CHECK (datos.genero IN ('M', 'F', 'O')),
    CONSTRAINT check_es_premium CHECK (datos.es_premium IN ('s', 'n'))
)
NESTED TABLE datos.fotos STORE AS nt_usuario_fotos;


CREATE TABLE Swipe(
    id_swipe NUMBER PRIMARY KEY,
    id_origen NUMBER NOT NULL,
    id_destino NUMBER NOT NULL,
    tipo_swipe VARCHAR2(10) CHECK (tipo_swipe IN ('LIKE', 'PASS', 'SUPERLIKE')),
    fecha_swipe DATE DEFAULT SYSDATE,
    CONSTRAINT fk_swipe_origen FOREIGN KEY (id_origen) REFERENCES Usuario(id_usuario),
    CONSTRAINT fk_swipe_destino FOREIGN KEY (id_destino) REFERENCES Usuario(id_usuario),
    CONSTRAINT check_no_self_swipe CHECK (id_origen != id_destino),
    CONSTRAINT swipe_unico UNIQUE (id_origen, id_destino)
);

CREATE TABLE Match(
    id_match NUMBER PRIMARY KEY,
    id_usuario1 NUMBER,
    id_usuario2 NUMBER,
    fecha_match DATE,
    activo NUMBER(1) DEFAULT 1 CHECK (activo IN (0, 1)),
    CONSTRAINT fk_match_usuario1 FOREIGN KEY (id_usuario1) REFERENCES Usuario(id_usuario),
    CONSTRAINT fk_match_usuario2 FOREIGN KEY (id_usuario2) REFERENCES Usuario(id_usuario),
    CONSTRAINT check_usuario_distintos CHECK (id_usuario1 < id_usuario2)
);

CREATE TABLE Mensaje(
    id_mensaje NUMBER PRIMARY KEY,
    id_match NUMBER NOT NULL,
    id_emisor NUMBER NOT NULL,
    texto VARCHAR2(500),
    fecha_envio DATE DEFAULT SYSDATE,
    CONSTRAINT fk_mensaje FOREIGN KEY (id_match) REFERENCES Match(id_match),
    CONSTRAINT fk_mensaje_emisor FOREIGN KEY (id_emisor) REFERENCES Usuario(id_usuario)
);

CREATE TABLE ArchivoMultimedia(
    id_archivo NUMBER PRIMARY KEY,
    id_usuario NUMBER,
    tipo_archivo VARCHAR2(10) CHECK (tipo_archivo IN ('foto', 'video')),
    url VARCHAR2(255),
    fecha_subida DATE DEFAULT SYSDATE,
    CONSTRAINT fk_archivo_usuario FOREIGN KEY (id_usuario) REFERENCES Usuario(id_usuario)
);

-- FIX: Eliminada la FK duplicada en la definición de columna;
--      solo se mantiene el CONSTRAINT con nombre
CREATE TABLE PreferenciaBusqueda (
    id_usuario NUMBER PRIMARY KEY,
    edad_min NUMBER(2) CHECK (edad_min >= 18),
    edad_max NUMBER(2),
    genero_interes CHAR CHECK (genero_interes IN ('M', 'F', 'O', 'A')),
    distancia_maxima NUMBER(5,2),
    CONSTRAINT fk_preferencia_usuario FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario) ON DELETE CASCADE,
    CONSTRAINT chk_edad_max CHECK (edad_max >= edad_min)  -- FIX: constraint de tabla, no de columna
);


-- ==========================================
-- 4. TRIGGERS
-- ==========================================

CREATE OR REPLACE TRIGGER trg_verificar_fechas_premium
BEFORE INSERT OR UPDATE ON Usuario FOR EACH ROW
DECLARE
    v_premium_data TipoUsuarioPremium;
BEGIN
    IF :NEW.datos IS OF (TipoUsuarioPremium) THEN
        v_premium_data := TREAT(:NEW.datos AS TipoUsuarioPremium);

        IF v_premium_data.fecha_fin <= v_premium_data.fecha_inicio OR v_premium_data.fecha_fin < SYSDATE THEN
            :NEW.datos := TipoUsuarioFree(
                v_premium_data.nombre, v_premium_data.correo, v_premium_data.contrasena,
                v_premium_data.fecha_nacimiento, v_premium_data.carrera, v_premium_data.descripcion,
                v_premium_data.genero, 'n', v_premium_data.ubicacion, v_premium_data.fotos, 20
            );
        ELSE
            NULL;
        END IF;

    ELSIF :NEW.datos IS OF (TipoUsuarioFree) THEN
        -- FIX: sustituido v_premium_data.fotos (no inicializado aquí) por :NEW.datos.fotos
        :NEW.datos := TipoUsuarioFree(
            :NEW.datos.nombre, :NEW.datos.correo, :NEW.datos.contrasena,
            :NEW.datos.fecha_nacimiento, :NEW.datos.carrera, :NEW.datos.descripcion,
            :NEW.datos.genero, 'n', :NEW.datos.ubicacion, :NEW.datos.fotos,
            TREAT(:NEW.datos AS TipoUsuarioFree).numero_swipes_disponibles
        );
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_control_superlike
BEFORE INSERT ON Swipe
FOR EACH ROW
DECLARE
    v_datos_usuario TipoUsuario;
BEGIN
    IF :NEW.tipo_swipe = 'SUPERLIKE' THEN
        SELECT datos INTO v_datos_usuario
        FROM Usuario
        WHERE id_usuario = :NEW.id_origen;

        IF NOT (v_datos_usuario IS OF (TipoUsuarioPremium)) THEN
            RAISE_APPLICATION_ERROR(-20003, 'Error: El Superlike es una función exclusiva para usuarios Premium.');
        END IF;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_control_swipes
BEFORE INSERT ON Swipe
FOR EACH ROW
DECLARE
    -- FIX: eliminado PRAGMA AUTONOMOUS_TRANSACTION — causaba ORA-01403 porque
    -- la transacción autónoma no veía los datos no confirmados de la sesión principal
    v_datos_usuario TipoUsuario;
    v_usuario_free TipoUsuarioFree;
BEGIN
    SELECT datos INTO v_datos_usuario
    FROM Usuario
    WHERE id_usuario = :NEW.id_origen;

    IF v_datos_usuario IS OF (TipoUsuarioFree) THEN
        v_usuario_free := TREAT(v_datos_usuario AS TipoUsuarioFree);

        IF v_usuario_free.numero_swipes_disponibles <= 0 THEN
            RAISE_APPLICATION_ERROR(-20004, 'Has agotado tus swipes diarios. Vuelve en 24h o hazte Premium');
        END IF;

        UPDATE Usuario
        SET datos = TipoUsuarioFree(
            v_usuario_free.nombre,
            v_usuario_free.correo,
            v_usuario_free.contrasena,
            v_usuario_free.fecha_nacimiento,
            v_usuario_free.carrera,
            v_usuario_free.descripcion,
            v_usuario_free.genero,
            v_usuario_free.es_premium,
            v_usuario_free.ubicacion,
            v_usuario_free.fotos,
            v_usuario_free.numero_swipes_disponibles - 1
        )
        WHERE id_usuario = :NEW.id_origen;
        -- FIX: eliminado COMMIT — en un trigger BEFORE sin transacción autónoma
        -- el commit lo gestiona la transacción principal (registrar_swipe)
    END IF;
END;
/


-- ==========================================
-- 5. PAQUETE: ESPECIFICACIÓN
-- ==========================================
CREATE OR REPLACE PACKAGE pkg_esiligue AS

    PROCEDURE crear_match (
        p_id_usuarioA IN NUMBER,
        p_id_usuarioB IN NUMBER
    );

    PROCEDURE registrar_swipe (
        p_id_origen IN NUMBER,
        p_id_destino IN NUMBER,
        p_tipo_swipe IN VARCHAR2
    );

    PROCEDURE caduca_premium(
        p_id_usuario IN NUMBER
    );

    PROCEDURE sp_reiniciar_swipes_diarios;

END pkg_esiligue;
/

-- ==========================================
-- 5. PAQUETE: CUERPO
-- ==========================================
CREATE OR REPLACE PACKAGE BODY pkg_esiligue AS

    PROCEDURE crear_match (
        p_id_usuarioA IN NUMBER,
        p_id_usuarioB IN NUMBER
    ) AS
        v_id_usuario1 NUMBER;
        v_id_usuario2 NUMBER;
    BEGIN
        IF p_id_usuarioA = p_id_usuarioB THEN
            RAISE_APPLICATION_ERROR(-20001, 'Error: Un usuario no puede hacer match consigo mismo.');
        END IF;

        IF p_id_usuarioA < p_id_usuarioB THEN
            v_id_usuario1 := p_id_usuarioA;
            v_id_usuario2 := p_id_usuarioB;
        ELSE
            v_id_usuario1 := p_id_usuarioB;
            v_id_usuario2 := p_id_usuarioA;
        END IF;

        INSERT INTO Match (id_match, id_usuario1, id_usuario2, fecha_match, activo)
        VALUES (id_match.NEXTVAL, v_id_usuario1, v_id_usuario2, SYSDATE, 1);

        DBMS_OUTPUT.PUT_LINE('Match creado con éxito entre ' || v_id_usuario1 || ' y ' || v_id_usuario2);

    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
            ROLLBACK;
            RAISE_APPLICATION_ERROR(-20002, 'Error: Ya existe un match entre estos dos usuarios.');
        WHEN OTHERS THEN
            ROLLBACK;
            RAISE_APPLICATION_ERROR(-20000, 'Error inesperado al crear el match: ' || SQLERRM);
    END crear_match;

    -- ==========================================
    PROCEDURE registrar_swipe (
        p_id_origen IN NUMBER,
        p_id_destino IN NUMBER,
        p_tipo_swipe IN VARCHAR2
    ) AS
        v_reciproco_tipo VARCHAR2(10);
    BEGIN
        INSERT INTO Swipe (id_swipe, id_origen, id_destino, tipo_swipe, fecha_swipe)
        VALUES (id_swipe.NEXTVAL, p_id_origen, p_id_destino, p_tipo_swipe, SYSDATE);

        IF p_tipo_swipe IN ('LIKE', 'SUPERLIKE') THEN
            BEGIN
                SELECT tipo_swipe INTO v_reciproco_tipo
                FROM Swipe
                WHERE id_origen = p_id_destino AND id_destino = p_id_origen;

                IF v_reciproco_tipo IN ('LIKE', 'SUPERLIKE') THEN
                    crear_match(p_id_origen, p_id_destino);
                END IF;
            EXCEPTION
                WHEN NO_DATA_FOUND THEN
                    NULL;
            END;
        END IF;

        COMMIT;
    END registrar_swipe;

    -- ==========================================
    PROCEDURE caduca_premium(
        p_id_usuario IN NUMBER
    ) AS
    BEGIN
        UPDATE Usuario u
        SET u.datos = TipoUsuarioFree(
            u.datos.nombre,
            u.datos.correo,
            u.datos.contrasena,
            u.datos.fecha_nacimiento,
            u.datos.carrera,
            u.datos.descripcion,
            u.datos.genero,
            'n',
            u.datos.ubicacion,
            u.datos.fotos,
            20
        )
        WHERE u.id_usuario = p_id_usuario;

        IF SQL%ROWCOUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20003, 'Error: No se encontró el usuario con ID ' || p_id_usuario);
        END IF;

        COMMIT;
        DBMS_OUTPUT.PUT_LINE('El usuario ' || p_id_usuario || ' ha pasado a ser Free correctamente.');

    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;
            RAISE_APPLICATION_ERROR(-20000, 'Error al degradar al usuario a Free: ' || SQLERRM);
    END caduca_premium;

    -- ==========================================
    PROCEDURE sp_reiniciar_swipes_diarios IS
    BEGIN
        UPDATE Usuario u
        SET datos = TipoUsuarioFree(
            TREAT(u.datos AS TipoUsuarioFree).nombre,
            TREAT(u.datos AS TipoUsuarioFree).correo,
            TREAT(u.datos AS TipoUsuarioFree).contrasena,
            TREAT(u.datos AS TipoUsuarioFree).fecha_nacimiento,
            TREAT(u.datos AS TipoUsuarioFree).carrera,
            TREAT(u.datos AS TipoUsuarioFree).descripcion,
            TREAT(u.datos AS TipoUsuarioFree).genero,
            TREAT(u.datos AS TipoUsuarioFree).es_premium,
            TREAT(u.datos AS TipoUsuarioFree).ubicacion,
            TREAT(u.datos AS TipoUsuarioFree).fotos,
            10
        )
        WHERE u.datos IS OF (TipoUsuarioFree);

        COMMIT;
    END sp_reiniciar_swipes_diarios;

END pkg_esiligue;
/


-- ==========================================
-- INSERCIÓN DE DATOS DE PRUEBA
-- ==========================================

-- FIX: todos los INSERT incluyen el campo fotos (TipoListaFotos())

-- Usuario 1: Free (Hombre)
INSERT INTO Usuario (id_usuario, datos)
VALUES (id_usuario.NEXTVAL,
    TipoUsuarioFree(
        'Juan Pérez', 'juan.perez@alum.uca.es', 'pass123',
        TO_DATE('1995-03-10', 'YYYY-MM-DD'), 'Ingeniería Informática',
        'Amante de la tecnología y el senderismo.', 'M', 'n',
        TipoUbicacion('Calle Mayor 1', 'Madrid', 40.4167, -3.7037),
        TipoListaFotos(),
        10
    )
);

-- Usuario 2: Free (Mujer)
INSERT INTO Usuario (id_usuario, datos)
VALUES (id_usuario.NEXTVAL,
    TipoUsuarioFree(
        'María García', 'm.garcia@alum.uca.es', 'secure456',
        TO_DATE('1997-07-22', 'YYYY-MM-DD'), 'Bellas Artes',
        'Pintora aficionada buscando nuevas aventuras.', 'F', 'n',
        TipoUbicacion('Gran Vía 45', 'Madrid', 40.4192, -3.7058),
        TipoListaFotos(),
        10
    )
);

-- Usuario 3: Premium (Hombre)
INSERT INTO Usuario (id_usuario, datos)
VALUES (id_usuario.NEXTVAL,
    TipoUsuarioPremium(
        'Carlos Ruiz', 'carlos.premium@alum.uca.es', 'premium789',
        TO_DATE('1990-11-05', 'YYYY-MM-DD'), 'Administración de Empresas',
        'Viajero empedernido y deportista.', 'M', 's',
        TipoUbicacion('Paseo de Gracia 12', 'Barcelona', 41.3851, 2.1734),
        TipoListaFotos(),
        SYSDATE, SYSDATE + 30
    )
);

-- Usuario 4: Premium (Mujer)
INSERT INTO Usuario (id_usuario, datos)
VALUES (id_usuario.NEXTVAL,
    TipoUsuarioPremium(
        'Lucía Fernández', 'lucia.f@alum.uca.es', 'lucia99',
        TO_DATE('1999-01-30', 'YYYY-MM-DD'), 'Medicina',
        'Estudiante de último año, me encanta el café.', 'F', 's',
        TipoUbicacion('Calle Balmes 8', 'Barcelona', 41.3900, 2.1600),
        TipoListaFotos(),
        SYSDATE, SYSDATE + 365
    )
);

-- 2. Preferencias de Búsqueda
INSERT INTO PreferenciaBusqueda (id_usuario, edad_min, edad_max, genero_interes, distancia_maxima)
SELECT id_usuario, 18, 30, 'F', 50.00 FROM Usuario u WHERE u.datos.correo = 'juan.perez@alum.uca.es';

INSERT INTO PreferenciaBusqueda (id_usuario, edad_min, edad_max, genero_interes, distancia_maxima)
SELECT id_usuario, 20, 35, 'M', 30.00 FROM Usuario u WHERE u.datos.correo = 'm.garcia@alum.uca.es';

INSERT INTO PreferenciaBusqueda (id_usuario, edad_min, edad_max, genero_interes, distancia_maxima)
SELECT id_usuario, 25, 45, 'A', 100.00 FROM Usuario u WHERE u.datos.correo = 'carlos.premium@alum.uca.es';

INSERT INTO PreferenciaBusqueda (id_usuario, edad_min, edad_max, genero_interes, distancia_maxima)
SELECT id_usuario, 22, 40, 'M', 20.00 FROM Usuario u WHERE u.datos.correo = 'lucia.f@alum.uca.es';

-- 3. Archivos Multimedia
INSERT INTO ArchivoMultimedia (id_archivo, id_usuario, tipo_archivo, url, fecha_subida)
SELECT id_archivo.NEXTVAL, id_usuario, 'foto', 'https://api.myapp.com/photos/user1_1.jpg', SYSDATE
FROM Usuario u WHERE u.datos.correo = 'juan.perez@alum.uca.es';

INSERT INTO ArchivoMultimedia (id_archivo, id_usuario, tipo_archivo, url, fecha_subida)
SELECT id_archivo.NEXTVAL, id_usuario, 'foto', 'https://api.myapp.com/photos/user2_1.jpg', SYSDATE
FROM Usuario u WHERE u.datos.correo = 'm.garcia@alum.uca.es';

INSERT INTO ArchivoMultimedia (id_archivo, id_usuario, tipo_archivo, url, fecha_subida)
SELECT id_archivo.NEXTVAL, id_usuario, 'video', 'https://api.myapp.com/videos/user3_intro.mp4', SYSDATE
FROM Usuario u WHERE u.datos.correo = 'carlos.premium@alum.uca.es';

-- 4. Swipes
DECLARE
    v_id_juan NUMBER;
    v_id_maria NUMBER;
    v_id_carlos NUMBER;
    v_id_lucia NUMBER;
BEGIN
    SELECT id_usuario INTO v_id_juan   FROM Usuario u WHERE u.datos.correo = 'juan.perez@alum.uca.es';
    SELECT id_usuario INTO v_id_maria  FROM Usuario u WHERE u.datos.correo = 'm.garcia@alum.uca.es';
    SELECT id_usuario INTO v_id_carlos FROM Usuario u WHERE u.datos.correo = 'carlos.premium@alum.uca.es';
    SELECT id_usuario INTO v_id_lucia  FROM Usuario u WHERE u.datos.correo = 'lucia.f@alum.uca.es';

    pkg_esiligue.registrar_swipe(v_id_juan,   v_id_maria,  'LIKE');
    pkg_esiligue.registrar_swipe(v_id_maria,  v_id_juan,   'LIKE');
    pkg_esiligue.registrar_swipe(v_id_carlos, v_id_lucia,  'SUPERLIKE');
    pkg_esiligue.registrar_swipe(v_id_lucia,  v_id_carlos, 'LIKE');
END;
/

-- 5. Mensajes
INSERT INTO Mensaje (id_mensaje, id_match, id_emisor, texto, fecha_envio)
SELECT id_mensaje.NEXTVAL, m.id_match,
       (SELECT id_usuario FROM Usuario u WHERE u.datos.correo = 'juan.perez@alum.uca.es'),
       '¡Hola María! ¿Cómo va todo?', SYSDATE
FROM Match m
WHERE m.id_usuario1 = (SELECT id_usuario FROM Usuario u WHERE u.datos.correo = 'juan.perez@alum.uca.es')
  AND m.id_usuario2 = (SELECT id_usuario FROM Usuario u WHERE u.datos.correo = 'm.garcia@alum.uca.es');

INSERT INTO Mensaje (id_mensaje, id_match, id_emisor, texto, fecha_envio)
SELECT id_mensaje.NEXTVAL, m.id_match,
       (SELECT id_usuario FROM Usuario u WHERE u.datos.correo = 'm.garcia@alum.uca.es'),
       'Hola Juan, ¡encantada de conocerte!', SYSDATE
FROM Match m
WHERE m.id_usuario1 = (SELECT id_usuario FROM Usuario u WHERE u.datos.correo = 'juan.perez@alum.uca.es')
  AND m.id_usuario2 = (SELECT id_usuario FROM Usuario u WHERE u.datos.correo = 'm.garcia@alum.uca.es');

COMMIT;