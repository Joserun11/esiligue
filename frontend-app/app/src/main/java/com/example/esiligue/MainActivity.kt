package com.example.esiligue

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import retrofit2.Call

// --- COLORES CORPORATIVOS ---
val AzulUCA = Color(0xFF00609D)
val AzulOscuroUCA = Color(0xFF004A7A)
val NaranjaUCA = Color(0xFFF49C1E)
val FondoGris = Color(0xFFF1F3F5)
val GrisSuave = Color(0xFFE0E0E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ESILigueApp()
                }
            }
        }
    }
}

// --- MODELOS DE DATOS LOCALES ---
data class PerfilFake(
    val id: Long,
    val nombre: String,
    val edad: Int,
    val carrera: String,
    val bio: String,
    val genero: String,
    val fotoUrl: String? = null,
    var meHaDadoLike: Boolean = false,
    var esSuperLike: Boolean = false
)

data class Mensaje(
    val id: String = UUID.randomUUID().toString(),
    val texto: String,
    val esMio: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

fun mapearGeneroDeOracle(codigoOracle: String?): String {
    return when (codigoOracle?.uppercase()?.trim()) {
        "M" -> "Chico"
        "F" -> "Chica"
        "O" -> "Otro"
        else -> codigoOracle ?: "Otro"
    }
}

fun descomponerNombreCompleto(nombreCompleto: String?): Pair<String, String> {
    val texto = nombreCompleto?.trim().orEmpty()
    if (texto.isBlank()) return "" to ""

    val partes = texto.split(Regex("\\s+"))
    val nombre = partes.firstOrNull().orEmpty()
    val apellido = if (partes.size > 1) partes.drop(1).joinToString(" ") else ""
    return nombre to apellido
}

fun normalizarFechaNacimientoDesdeApi(fecha: String?): String {
    if (fecha.isNullOrBlank()) return ""

    val soloDigitos = fecha.filter { it.isDigit() }
    if (soloDigitos.length >= 8) {
        val primeros8 = soloDigitos.take(8)
        // Oracle suele enviar YYYYMMDD..., la app trabaja con DDMMAAAA al guardar.
        return if (primeros8.startsWith("19") || primeros8.startsWith("20")) {
            primeros8.substring(6, 8) + primeros8.substring(4, 6) + primeros8.substring(0, 4)
        } else {
            primeros8
        }
    }

    return ""
}

fun calcularEdadDesdeFechaApi(fecha: String?): Int {
    val fechaNormalizada = normalizarFechaNacimientoDesdeApi(fecha)
    if (fechaNormalizada.length != 8) return 0

    return try {
        val parser = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
        parser.isLenient = false
        val fechaDate = parser.parse(fechaNormalizada) ?: return 0

        val hoy = java.util.Calendar.getInstance()
        val nacimiento = java.util.Calendar.getInstance().apply { time = fechaDate }

        var edad = hoy.get(java.util.Calendar.YEAR) - nacimiento.get(java.util.Calendar.YEAR)
        if (
            hoy.get(java.util.Calendar.MONTH) < nacimiento.get(java.util.Calendar.MONTH) ||
            (hoy.get(java.util.Calendar.MONTH) == nacimiento.get(java.util.Calendar.MONTH) &&
                hoy.get(java.util.Calendar.DAY_OF_MONTH) < nacimiento.get(java.util.Calendar.DAY_OF_MONTH))
        ) {
            edad--
        }
        if (edad < 0) 0 else edad
    } catch (_: Exception) {
        0
    }
}

// --- VIEWMODEL ---
class PerfilViewModel : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var isLogin by mutableStateOf(true)
    var isPremium by mutableStateOf(false)
    var miIdReal by mutableLongStateOf(0L)

    var nombre by mutableStateOf("")
    var apellido by mutableStateOf("")
    var fechaNacimiento by mutableStateOf("")
    var mostrarAnimacionMatch by mutableStateOf<PerfilFake?>(null)
    var fotosDelPerfilActual = mutableStateListOf<String>()
    var ciudadResidencia by mutableStateOf("Cádiz")
    var ciudadBusqueda by mutableStateOf("Cádiz")

    fun cargarFotosParaPerfil(userId: Long) {
        RetrofitClient.instance.obtenerFotosDeUsuario(userId).enqueue(object : retrofit2.Callback<List<String>> {
            override fun onResponse(call: retrofit2.Call<List<String>>, response: retrofit2.Response<List<String>>) {
                fotosDelPerfilActual.clear()
                if (response.isSuccessful) {
                    response.body()?.let { urls ->
                        fotosDelPerfilActual.addAll(urls)
                    }
                }
            }
            override fun onFailure(call: retrofit2.Call<List<String>>, t: Throwable) {
                println("Error al cargar fotos: ${t.message}")
            }
        })
    }

    private fun fotoPrincipalDeUsuario(usuario: Usuario): String? {
        return listOf(usuario.foto1, usuario.foto2, usuario.foto3, usuario.foto4, usuario.foto5, usuario.foto6)
            .firstOrNull { !it.isNullOrBlank() }
    }

    // Función que calcula los años exactos y comprueba si la fecha existe de verdad
    fun obtenerEdad(): Int {
        val fechaLimpia = fechaNacimiento.trim()
        if (fechaLimpia.isBlank()) return -1

        val candidatos = listOf(fechaLimpia, fechaLimpia.filter { it.isDigit() }).distinct()
        val formatos = listOf("ddMMyyyy", "yyyyMMdd", "dd/MM/yyyy", "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss")

        var fechaParseada: java.util.Date? = null
        for (valor in candidatos) {
            for (formato in formatos) {
                try {
                    val parser = SimpleDateFormat(formato, Locale.getDefault())
                    parser.isLenient = false
                    val parsed = parser.parse(valor)
                    if (parsed != null) {
                        fechaParseada = parsed
                        break
                    }
                } catch (_: Exception) {
                }
            }
            if (fechaParseada != null) break
        }

        if (fechaParseada == null) return -1

        return try {
            val hoy = java.util.Calendar.getInstance()
            val nacimiento = java.util.Calendar.getInstance().apply { time = fechaParseada }

            var edadCalc = hoy.get(java.util.Calendar.YEAR) - nacimiento.get(java.util.Calendar.YEAR)
            if (
                hoy.get(java.util.Calendar.MONTH) < nacimiento.get(java.util.Calendar.MONTH) ||
                (hoy.get(java.util.Calendar.MONTH) == nacimiento.get(java.util.Calendar.MONTH) &&
                    hoy.get(java.util.Calendar.DAY_OF_MONTH) < nacimiento.get(java.util.Calendar.DAY_OF_MONTH))
            ) {
                edadCalc--
            }
            edadCalc
        } catch (_: Exception) {
            -1
        }
    }

    val isFechaNacimientoValid get() = obtenerEdad() in 18..49
    var genero by mutableStateOf("")
    var queBusco by mutableStateOf("")
    var rangoEdadBusqueda by mutableStateOf(18f..49f)
    var carrera by mutableStateOf("")
    var bio by mutableStateOf("")
    val fotosSelected = mutableStateListOf<Uri>()

    val quienesMeDieronLike = mutableStateListOf<PerfilFake>()
    val matchesConfirmados = mutableStateListOf<PerfilFake>()
    val aQuienDiLike = mutableStateListOf<PerfilFake>()

    private val historialMensajes = mutableMapOf<Long, SnapshotStateList<Mensaje>>()

    // Ahora empieza vacía y solo se llenará cuando Oracle mande los datos reales
    val perfilesDisponibles = mutableStateListOf<PerfilFake>()

    val isEmailValid get() = email.contains("@uca.es") || email.contains("@alum.uca.es")
    val isPasswordLengthValid get() = password.length >= 6
    val passwordsMatch get() = password == confirmPassword
    val canSubmitAuth get() = isEmailValid && isPasswordLengthValid && (isLogin || passwordsMatch)
    val canContinueDatos get() = nombre.isNotBlank() && apellido.isNotBlank() && isFechaNacimientoValid && genero.isNotBlank() && queBusco.isNotBlank() && carrera.isNotBlank()
    val canFinishFotos get() = fotosSelected.size in 3..6 && bio.length in 20..150

    fun darLike(perfil: PerfilFake, tipo: String) {
        val nuevoSwipe = Swipe(
            id_origen = miIdReal,
            id_destino = perfil.id,
            tipo_swipe = tipo
        )

        RetrofitClient.instance.registrarSwipe(nuevoSwipe).enqueue(object : retrofit2.Callback<String> {
            override fun onResponse(call: retrofit2.Call<String>, response: retrofit2.Response<String>) {
                if (response.isSuccessful && response.body() == "MATCH") {
                    // En lugar de devolver un booleano, activamos la "señal" de Match
                    mostrarAnimacionMatch = perfil
                }
            }
            override fun onFailure(call: retrofit2.Call<String>, t: Throwable) {
                // Log de error opcional
            }
        })

        // Esto hace que la tarjeta desaparezca inmediatamente
        perfilesDisponibles.remove(perfil)
    }

    fun darSuperLike(perfil: PerfilFake) {
        perfil.esSuperLike = true
        matchesConfirmados.add(perfil)
        quienesMeDieronLike.remove(perfil)
        perfilesDisponibles.remove(perfil)
    }

    fun cargarDatosDeMemoria(sharedPreferences: SharedPreferences) {
        miIdReal = sharedPreferences.getLong("idUsuarioReal", 0L)
        email = sharedPreferences.getString("emailRegistrado", "") ?: ""
        password =
            sharedPreferences.getString("passwordUsuario", "") ?: "" // <--- ESTA ES LA LÍNEA NUEVA

        val nombrePersistido = sharedPreferences.getString("nombreUsuario", "Usuario") ?: "Usuario"
        val apellidoPersistido = sharedPreferences.getString("apellidoUsuario", "") ?: ""
        val (nombreBase, apellidoBase) = descomponerNombreCompleto(nombrePersistido)
        nombre = nombreBase.ifBlank { nombrePersistido }
        apellido = apellidoPersistido.ifBlank { apellidoBase }
        fechaNacimiento =
            sharedPreferences.getString("fechaNacimientoUsuario", "")?.filter { it.isDigit() } ?: ""
        carrera = sharedPreferences.getString("carreraUsuario", "") ?: ""
        bio = sharedPreferences.getString("bioUsuario", "") ?: ""
        genero = sharedPreferences.getString("generoUsuario", "") ?: ""
        queBusco = sharedPreferences.getString("queBuscoUsuario", "Todos") ?: "Todos"
        ciudadResidencia = sharedPreferences.getString("ciudadResidenciaUsuario", "Cádiz") ?: "Cádiz"
        ciudadBusqueda = sharedPreferences.getString("ciudadBusquedaUsuario", "Cádiz") ?: "Cádiz"

        val rangoInicio = sharedPreferences.getFloat("rangoEdadInicio", 18f)
        val rangoFin = sharedPreferences.getFloat("rangoEdadFin", 49f)
        rangoEdadBusqueda = rangoInicio..rangoFin

        isPremium = sharedPreferences.getBoolean("isPremiumUsuario", false)

        val fotosString = sharedPreferences.getString("fotosUsuario", "") ?: ""
        if (fotosString.isNotEmpty()) {
            fotosSelected.clear()
            val uris = fotosString.split(",").map { Uri.parse(it) }
            fotosSelected.addAll(uris)
        }
    }

    fun obtenerMensajes(perfilId: Long): SnapshotStateList<Mensaje> {
        return historialMensajes.getOrPut(perfilId) {
            mutableStateListOf(
                Mensaje(
                    texto = "¡Habéis hecho match! Da el primer paso 👋",
                    esMio = false,
                    timestamp = 0L
                )
            )
        }
    }

    fun enviarMensaje(perfilId: Long, texto: String) {
        if (texto.isNotBlank()) {
            obtenerMensajes(perfilId).add(Mensaje(texto = texto, esMio = true))
        }
    }

    fun eliminarMensaje(perfilId: Long, mensajeId: String) {
        val mensajes = obtenerMensajes(perfilId)
        mensajes.removeAll { it.id == mensajeId }
    }

    fun editarMensaje(perfilId: Long, mensajeId: String, nuevoTexto: String) {
        if (nuevoTexto.isBlank()) return
        val mensajes = obtenerMensajes(perfilId)
        val indice = mensajes.indexOfFirst { it.id == mensajeId }
        if (indice != -1) {
            mensajes[indice] = mensajes[indice].copy(texto = nuevoTexto)
        }
    }

    fun cargarUsuariosDeLaRed(usuariosRed: List<Usuario>) {
        perfilesDisponibles.clear()

        val perfilesReales = usuariosRed.map { usuarioOracle ->
            // Transformamos al usuario de Oracle para que la tarjeta lo pueda dibujar
            PerfilFake(
                id = usuarioOracle.id_usuario?.toLong() ?: 0,
                nombre = usuarioOracle.nombre ?: "Desconocido",
                edad = if ((usuarioOracle.edad ?: 0) > 0) usuarioOracle.edad ?: 0 else calcularEdadDesdeFechaApi(usuarioOracle.fecha_nacimiento),
                carrera = usuarioOracle.carrera ?: "Estudiante UCA",
                bio = usuarioOracle.bio ?: "No hay biografía.",
                genero = mapearGeneroDeOracle(usuarioOracle.genero),
                fotoUrl = fotoPrincipalDeUsuario(usuarioOracle),
                meHaDadoLike = false
            )
        }

        perfilesDisponibles.addAll(perfilesReales)


        if (perfilesDisponibles.isNotEmpty()) {
            // En tu mazo de cartas, la última de la lista es la que se dibuja arriba del todo
            val idPrimerPerfil = perfilesDisponibles.last().id.toLong()
            cargarFotosParaPerfil(idPrimerPerfil)
        }
    }

    fun limpiarDatosParaNuevaSesion() {
        email = ""
        password = ""
        confirmPassword = ""
        nombre = ""
        apellido = ""
        fechaNacimiento = ""
        genero = ""
        queBusco = ""
        rangoEdadBusqueda = 18f..49f
        carrera = ""
        bio = ""
        fotosSelected.clear()
        miIdReal = 0L
        // También limpiamos las listas de matches para que no se mezclen
        quienesMeDieronLike.clear()
        matchesConfirmados.clear()
        aQuienDiLike.clear()
    }

    fun cargarMatchesReales() {
        // Usamos el ID real. Asegúrate de que no sea 0L
        RetrofitClient.instance.obtenerMatches(miIdReal).enqueue(object : retrofit2.Callback<List<Usuario>> {
            override fun onResponse(call: retrofit2.Call<List<Usuario>>, response: retrofit2.Response<List<Usuario>>) {
                if (response.isSuccessful) {
                    matchesConfirmados.clear()
                    val lista = response.body()?.map { u ->
            
                        PerfilFake(
                            id = u.id_usuario?.toLong() ?: 0,
                            nombre = u.nombre ?: "Usuario ESILigue",
                            edad = if ((u.edad ?: 0) > 0) u.edad ?: 0 else calcularEdadDesdeFechaApi(u.fecha_nacimiento),
                            carrera = u.carrera ?: "Estudiante",
                            bio = u.bio ?: "",
                            genero = u.genero ?: "Otro",
                            fotoUrl = listOf(u.foto1, u.foto2, u.foto3, u.foto4, u.foto5, u.foto6).firstOrNull { !it.isNullOrBlank() },
                            meHaDadoLike = true // Si es un match, obviamente hay like
                        )
                    } ?: emptyList()
                    matchesConfirmados.addAll(lista)
                }
            }
            override fun onFailure(call: retrofit2.Call<List<Usuario>>, t: Throwable) {
                // Siempre es bueno poner un log para saber si falla la red
                println("Error al cargar matches: ${t.message}")
            }
        })
    }

    fun cargarLikesRecibidos() {
        if (miIdReal == 0L) return

        RetrofitClient.instance.obtenerLikesRecibidos(miIdReal).enqueue(object : retrofit2.Callback<List<Usuario>> {
            override fun onResponse(call: retrofit2.Call<List<Usuario>>, response: retrofit2.Response<List<Usuario>>) {
                if (response.isSuccessful) {
                    val lista = response.body()?.map { u ->
                        PerfilFake(
                            id = u.id_usuario?.toLong() ?: 0,
                            nombre = u.nombre ?: "Usuario ESILigue",
                            edad = if ((u.edad ?: 0) > 0) u.edad ?: 0 else calcularEdadDesdeFechaApi(u.fecha_nacimiento),
                            carrera = u.carrera ?: "Estudiante",
                            bio = u.bio ?: "",
                            genero = u.genero ?: "Otro",
                            fotoUrl = listOf(u.foto1, u.foto2, u.foto3, u.foto4, u.foto5, u.foto6).firstOrNull { !it.isNullOrBlank() },
                            meHaDadoLike = true
                        )
                    } ?: emptyList()

                    quienesMeDieronLike.clear()
                    quienesMeDieronLike.addAll(lista)
                }
            }

            override fun onFailure(call: retrofit2.Call<List<Usuario>>, t: Throwable) {
                println("Error al cargar likes recibidos: ${t.message}")
            }
        })
    }
}

// --- NAVEGACIÓN ---
@Composable
fun ESILigueApp() {
    val navController = rememberNavController()
    val vm: PerfilViewModel = viewModel()

    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("ESILiguePrefs", Context.MODE_PRIVATE) }
    val isLoggedIn = remember { sharedPreferences.getBoolean("isLoggedIn", false) }

    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            vm.cargarDatosDeMemoria(sharedPreferences)
        }
    }

    val startDest = if (isLoggedIn) "swipe" else "auth"

    NavHost(navController = navController, startDestination = startDest) {
        composable("auth") { PantallaAuth(navController, vm) }
        composable("perfil_setup") { PantallaDatosPersonales(navController, vm) }
        composable("fotos_setup") { PantallaSubirFotos(navController, vm) }
        composable("premium") { PantallaPremium(navController, vm) }
        composable("swipe") { PantallaPrincipalSwipe(navController, vm) }
        composable("mi_perfil") { PantallaMiPerfil(navController, vm) }
        composable("matches") { PantallaMatches(navController, vm) }

        composable("chat/{perfilId}") { backStackEntry ->
            val perfilId = backStackEntry.arguments?.getString("perfilId")?.toLongOrNull() ?: 0L
            val perfil = vm.matchesConfirmados.find { it.id == perfilId }
            if (perfil != null) {
                PantallaChat(navController, vm, perfil)
            }
        }
    }
}

// --- PANTALLA IT'S A MATCH ---
@Composable
fun PantallaItIsAMatch(miFoto: Uri?, suNombre: String, onCerrar: () -> Unit, onChat: () -> Unit) {
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AzulOscuroUCA.copy(alpha = 0.98f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.logo_esiligue),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
                Text(
                    text = "¡IT'S A MATCH!",
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
                Text(
                    text = "A $suNombre también le gustas",
                    color = Color.White,
                    fontSize = 20.sp
                )

                Spacer(Modifier.height(50.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(140.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape)) {
                        if (miFoto != null) {
                            AsyncImage(model = miFoto, contentDescription = null, contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.Gray))
                        }
                    }

                    Icon(
                        Icons.Default.Favorite,
                        null,
                        tint = Color.Red,
                        modifier = Modifier.size(60.dp).padding(horizontal = 12.dp)
                    )

                    Box(
                        Modifier.size(140.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).background(GrisSuave),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, Modifier.size(70.dp), AzulUCA)
                    }
                }

                Spacer(Modifier.height(60.dp))

                Button(
                    onClick = onChat,
                    modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(NaranjaUCA),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("ENVIAR MENSAJE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                TextButton(onClick = onCerrar, modifier = Modifier.padding(top = 16.dp)) {
                    Text("SEGUIR BUSCANDO", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// --- PANTALLA SWIPE ---
@Composable
fun PantallaPrincipalSwipe(navController: NavController, vm: PerfilViewModel) {
    val context = LocalContext.current
    var perfilSeleccionado by remember { mutableStateOf<PerfilFake?>(null) }

    LaunchedEffect(vm.miIdReal) {
        if (vm.miIdReal != 0L) {
            RetrofitClient.instance.obtenerUsuariosParaDescubrir(vm.miIdReal)
                .enqueue(object : retrofit2.Callback<List<Usuario>> {
                    override fun onResponse(call: retrofit2.Call<List<Usuario>>, response: retrofit2.Response<List<Usuario>>) {
                        if (response.isSuccessful) {
                            val usuariosFiltrados = response.body() ?: emptyList()
                            vm.perfilesDisponibles.clear()
                            vm.cargarUsuariosDeLaRed(usuariosFiltrados)
                        }
                    }
                    override fun onFailure(call: retrofit2.Call<List<Usuario>>, t: Throwable) {
                        Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    Scaffold(
        bottomBar = { BarraNavegacionInferior(navController, currentRoute = "swipe") }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(colors = listOf(AzulUCA.copy(alpha = 0.08f), FondoGris, FondoGris))
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painter = painterResource(id = R.drawable.logo_esiligue), contentDescription = "Logo", modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text("Descubrir", fontWeight = FontWeight.Black, fontSize = 24.sp, color = AzulOscuroUCA)
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = padding.calculateBottomPadding() + 10.dp),
                contentAlignment = Alignment.Center
            ) {
                val perfilesFiltrados = vm.perfilesDisponibles

                if (perfilesFiltrados.isNotEmpty()) {
                    val perfil = perfilesFiltrados.last()
                    TarjetaPerfil(
                        perfil = perfil,
                        fotoUrl = perfil.fotoUrl,

                        onPass = {
                            vm.darLike(perfil, "PASS")
                            vm.perfilesDisponibles.remove(perfil)
                  
                            if (vm.perfilesDisponibles.isNotEmpty()) {
                                vm.cargarFotosParaPerfil(vm.perfilesDisponibles.last().id.toLong())
                            }
                        },
                        onLike = {
                            vm.darLike(perfil, "LIKE")
                            Toast.makeText(context, "Like enviado", Toast.LENGTH_SHORT).show()
                           
                            if (vm.perfilesDisponibles.isNotEmpty()) {
                                vm.cargarFotosParaPerfil(vm.perfilesDisponibles.last().id.toLong())
                            }
                        },
                        onSuper = {
                            if (vm.isPremium) {
                                vm.darLike(perfil, "SUPERLIKE")
                            
                                if (vm.perfilesDisponibles.isNotEmpty()) {
                                    vm.cargarFotosParaPerfil(vm.perfilesDisponibles.last().id.toLong())
                                }
                            } else {
                                navController.navigate("premium")
                            }
                        },
                        onClick = {
                            vm.cargarFotosParaPerfil(perfil.id)
                            perfilSeleccionado = perfil
                        }
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(AzulUCA.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.WifiTethering, null, tint = AzulUCA.copy(alpha = 0.5f), modifier = Modifier.size(50.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("¡Estás al día!", fontWeight = FontWeight.Black, fontSize = 24.sp, color = AzulOscuroUCA)
                    }
                }
            }
        }

        if (vm.mostrarAnimacionMatch != null) {
            val match = vm.mostrarAnimacionMatch!!
            PantallaItIsAMatch(
                miFoto = vm.fotosSelected.firstOrNull(),
                suNombre = match.nombre,
                onCerrar = { vm.mostrarAnimacionMatch = null },
                onChat = {
                    val id = match.id
                    vm.mostrarAnimacionMatch = null
                    navController.navigate("chat/$id")
                }
            )
        }

        if (perfilSeleccionado != null) {
            PantallaDetallePerfil(
                perfil = perfilSeleccionado!!,
                vm = vm, // <--- PASAMOS EL VIEWMODEL AQUÍ
                onCerrar = { perfilSeleccionado = null }
            )
        }
    }
}

// --- MENU INFERIOR GLASSMORPHISM ---
@Composable
fun BarraNavegacionInferior(navController: NavController, currentRoute: String) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .height(70.dp)
            .fillMaxWidth(),
        color = Color.White.copy(alpha = 0.85f),
        shape = RoundedCornerShape(35.dp),
        border = BorderStroke(1.dp, Color.White),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BotonNavegacionCustom(
                icon = Icons.Default.Person,
                label = "Perfil",
                selected = currentRoute == "mi_perfil",
                onClick = { if (currentRoute != "mi_perfil") navController.navigate("mi_perfil") }
            )
            BotonNavegacionCustom(
                icon = Icons.Default.Whatshot,
                label = "Descubrir",
                selected = currentRoute == "swipe",
                onClick = { if (currentRoute != "swipe") navController.navigate("swipe") }
            )
            BotonNavegacionCustom(
                icon = Icons.Default.Favorite,
                label = "Matches",
                selected = currentRoute == "matches",
                onClick = { if (currentRoute != "matches") navController.navigate("matches") }
            )
        }
    }
}

@Composable
fun BotonNavegacionCustom(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) AzulUCA else Color.Gray
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = color)
    }
}

// --- TARJETA PERFIL ---
@Composable
fun TarjetaPerfil(
    perfil: PerfilFake,
    fotoUrl: String?, // 👇 CAMBIO 1: Añadimos la URL de la foto
    onPass: () -> Unit,
    onLike: () -> Unit,
    onSuper: () -> Unit = {},
    onClick: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    Card(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .fillMaxSize()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .graphicsLayer(rotationZ = offsetX / 20f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (offsetX > 400) onLike() else if (offsetX < -400) onPass()
                        offsetX = 0f
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        offsetX += drag.x
                    }
                )
            },
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {

        
            if (fotoUrl != null) {
                AsyncImage(
                    model = fotoUrl,
                    contentDescription = "Foto de ${perfil.nombre}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop // Recorta la imagen para que rellene toda la tarjeta
                )
            } else {
                // El placeholder (por si la persona no ha subido foto o tarda en cargar)
                Box(Modifier.fillMaxSize().background(GrisSuave), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, Modifier.size(120.dp), AzulUCA.copy(0.1f))
                }
            }
            

            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)), startY = 800f)))

            if (offsetX > 0) {
                Text(
                    "LIKE", color = Color.Green, fontWeight = FontWeight.Black, fontSize = 40.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(40.dp)
                        .graphicsLayer(rotationZ = -15f, alpha = (offsetX / 200f).coerceIn(0f, 1f))
                        .border(4.dp, Color.Green, RoundedCornerShape(10.dp)).padding(12.dp)
                )
            } else if (offsetX < 0) {
                Text(
                    "NOPE", color = Color.Red, fontWeight = FontWeight.Black, fontSize = 40.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(40.dp)
                        .graphicsLayer(rotationZ = 15f, alpha = (-offsetX / 200f).coerceIn(0f, 1f))
                        .border(4.dp, Color.Red, RoundedCornerShape(10.dp)).padding(12.dp)
                )
            }

            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(perfil.nombre, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(perfil.edad.toString(), color = Color.White.copy(0.8f), fontSize = 26.sp)
                }
                Surface(Modifier.padding(top = 8.dp), color = Color.White.copy(0.2f), shape = RoundedCornerShape(12.dp)) {
                    Text(perfil.carrera, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 30.dp), Arrangement.SpaceEvenly) {
                    LargeIconButton(Icons.Default.Close, Color.Gray, onClick = onPass)
                    LargeIconButton(Icons.Default.Favorite, Color.Red, 70.dp, onLike)
                    LargeIconButton(Icons.Default.Bolt, Color(0xFF22C55E), onClick = onSuper)
                }
            }
        }
    }
}

@Composable
fun LargeIconButton(icon: ImageVector, color: Color, size: androidx.compose.ui.unit.Dp = 60.dp, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(size).clickable { onClick() },
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.8f),
        border = BorderStroke(0.5.dp, Color.White),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.5f)) }
    }
}

@Composable
fun PantallaDetallePerfil(
    perfil: PerfilFake,
    vm: PerfilViewModel, // 1. Añadimos el ViewModel como parámetro
    onCerrar: () -> Unit
) {
    // Usamos un Dialog para que aparezca como una ventana flotante sobre el swipe
    Dialog(onDismissRequest = onCerrar) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f) // Que ocupe casi toda la pantalla de alto
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(Color.White),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()) // Por si la bio es muy larga
            ) {
                // --- SECCIÓN DE LA FOTO ---
                Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                    // 2. Usamos AsyncImage para pintar la foto de Oracle
                    if (vm.fotosDelPerfilActual.isNotEmpty()) {
                        AsyncImage(
                            model = vm.fotosDelPerfilActual.firstOrNull(),
                            contentDescription = "Foto de ${perfil.nombre}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop // Para que rellene bien el espacio
                        )
                    } else {
                        // Fondo gris con icono si no hay fotos todavía
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.LightGray.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(100.dp), tint = Color.Gray)
                        }
                    }

                    // Botón para cerrar (la X) arriba a la derecha
                    IconButton(
                        onClick = onCerrar,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                // --- SECCIÓN DE INFORMACIÓN ---
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = perfil.nombre,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = perfil.edad.toString(),
                            fontSize = 24.sp,
                            color = Color.Gray
                        )
                    }

                    Text(
                        text = perfil.carrera,
                        fontSize = 18.sp,
                        color = Color(0xFF0056B3), // Tu azul UCA
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), thickness = 1.dp, color = Color.LightGray)

                    Text(
                        text = "Sobre mí",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )

                    Text(
                        text = perfil.bio,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
fun InfoCard(icon: ImageVector, text: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(FondoGris), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AzulUCA, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun CustomTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, isPassword: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text, isError: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = AzulUCA) },
        trailingIcon = { if (isPassword) IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = AzulUCA) } },
        modifier = Modifier.fillMaxWidth(), singleLine = true, isError = isError,
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AzulUCA, errorBorderColor = Color.Red)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PantallaChat(navController: NavController, vm: PerfilViewModel, perfil: PerfilFake) {
    val context = LocalContext.current
    var mensajeTexto by remember { mutableStateOf("") }
    val mensajes = vm.obtenerMensajes(perfil.id)

    var mensajeOpciones by remember { mutableStateOf<Mensaje?>(null) }
    var mensajeAEditar by remember { mutableStateOf<Mensaje?>(null) }
    var textoEdicion by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(GrisSuave), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = AzulUCA)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(perfil.nombre, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AzulOscuroUCA)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = AzulUCA) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = mensajeTexto, onValueChange = { mensajeTexto = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un mensaje...") }, shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AzulUCA, unfocusedBorderColor = Color.LightGray), maxLines = 3
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { vm.enviarMensaje(perfil.id, mensajeTexto); mensajeTexto = "" },
                        modifier = Modifier.size(48.dp).background(if (mensajeTexto.isNotBlank()) AzulUCA else Color.Gray, CircleShape),
                        enabled = mensajeTexto.isNotBlank()
                    ) { Icon(Icons.Default.Send, "Enviar", tint = Color.White) }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(FondoGris).padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), reverseLayout = false
        ) {
            items(mensajes) { msg ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (msg.esMio) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (msg.esMio) 16.dp else 4.dp, bottomEnd = if (msg.esMio) 4.dp else 16.dp),
                        color = if (msg.esMio) AzulUCA else Color.White, shadowElevation = 2.dp,
                        modifier = Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = {
                                if (msg.esMio && msg.timestamp > 0) {
                                    if (System.currentTimeMillis() - msg.timestamp <= 15 * 60 * 1000L) mensajeOpciones = msg
                                    else Toast.makeText(context, "Pasaron los 15 min. Ya no puedes modificarlo.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    ) { Text(msg.texto, color = if (msg.esMio) Color.White else Color.Black, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 16.sp) }
                }
            }
        }

        if (mensajeOpciones != null) {
            AlertDialog(
                onDismissRequest = { mensajeOpciones = null },
                title = { Text("Acción requerida", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { textoEdicion = mensajeOpciones!!.texto; mensajeAEditar = mensajeOpciones; mensajeOpciones = null }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, AzulUCA)) { Text("✏️ Editar Mensaje", color = AzulUCA) }
                        OutlinedButton(onClick = { vm.eliminarMensaje(perfil.id, mensajeOpciones!!.id); mensajeOpciones = null; Toast.makeText(context, "Mensaje eliminado", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color.Red)) { Text("🗑️ Eliminar Mensaje", color = Color.Red) }
                    }
                },
                confirmButton = { TextButton(onClick = { mensajeOpciones = null }) { Text("Cancelar", color = Color.Gray) } }
            )
        }

        if (mensajeAEditar != null) {
            AlertDialog(
                onDismissRequest = { mensajeAEditar = null },
                title = { Text("Editar mensaje", fontWeight = FontWeight.Bold, color = AzulUCA) },
                text = { OutlinedTextField(value = textoEdicion, onValueChange = { textoEdicion = it }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AzulUCA)) },
                confirmButton = { Button(onClick = { vm.editarMensaje(perfil.id, mensajeAEditar!!.id, textoEdicion); mensajeAEditar = null }, colors = ButtonDefaults.buttonColors(AzulUCA)) { Text("Guardar Cambios") } },
                dismissButton = { TextButton(onClick = { mensajeAEditar = null }) { Text("Cancelar", color = Color.Gray) } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMatches(navController: NavController, vm: PerfilViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Matches", "Le gustas", "Mis likes")

    LaunchedEffect(Unit) {
        vm.cargarMatchesReales()
        vm.cargarLikesRecibidos()
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(title = { Text("Mensajes y Likes", fontWeight = FontWeight.Bold, color = AzulUCA) })
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = AzulUCA) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        )
                    }
                }
            }
        },
        bottomBar = { BarraNavegacionInferior(navController, currentRoute = "matches") }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(FondoGris)) {
            when (selectedTab) {
                0 -> ListaDeMatches(vm.matchesConfirmados, "match", onChatClick = { navController.navigate("chat/${it.id}") })

                1 -> {
                    val leGustasFiltrado = vm.quienesMeDieronLike.filter { persona ->
                        when (vm.queBusco) {
                            "Chicas" -> persona.genero == "Chica"
                            "Chicos" -> persona.genero == "Chico"
                            else -> true
                        }
                    }
                    ListaDeMatches(leGustasFiltrado, "le_gustas", onLikeBack = { perfil ->
                        vm.darLike(perfil, "LIKE")
                    })
                }

                2 -> ListaDeMatches(vm.aQuienDiLike, "mis_likes")
            }
        }
    }
}

@Composable
fun ListaDeMatches(lista: List<PerfilFake>, tipoLista: String, onLikeBack: ((PerfilFake) -> Unit)? = null, onChatClick: ((PerfilFake) -> Unit)? = null) {
    if (lista.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay nada por aquí todavía.", color = Color.Gray) }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(lista) { perfil ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(GrisSuave), contentAlignment = Alignment.Center) {
                            if (!perfil.fotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = perfil.fotoUrl,
                                    contentDescription = "Foto de ${perfil.nombre}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, null, tint = AzulUCA)
                            }
                        }
                        Spacer(Modifier.width(15.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(perfil.nombre, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Text(perfil.edad.toString(), color = Color.Gray, fontSize = 12.sp)
                            }
                            Text(perfil.carrera, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (perfil.esSuperLike) Icon(Icons.Default.Bolt, "Superlike", tint = Color(0xFF22C55E))
                        when (tipoLista) {
                            "match" -> IconButton(onClick = { onChatClick?.invoke(perfil) }) { Icon(Icons.AutoMirrored.Filled.Chat, "Chat", tint = AzulUCA) }
                            "le_gustas" -> Button(onClick = { onLikeBack?.invoke(perfil) }, colors = ButtonDefaults.buttonColors(NaranjaUCA), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("Like back", fontSize = 12.sp) }
                            "mis_likes" -> Text("Esperando", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PantallaMiPerfil(navController: NavController, vm: PerfilViewModel) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("ESILiguePrefs", Context.MODE_PRIVATE)
    var mostrarDialogoCerrarSesion by remember { mutableStateOf(false) }
    var mostrarDialogoEliminarCuenta by remember { mutableStateOf(false) }
    var mostrarDialogoCancelarPremium by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.cargarDatosDeMemoria(sharedPreferences)

        val loginRequest = Usuario(correo = vm.email, contrasena = vm.password)
        RetrofitClient.instance.login(loginRequest).enqueue(object : retrofit2.Callback<Usuario> {
            override fun onResponse(call: retrofit2.Call<Usuario>, response: retrofit2.Response<Usuario>) {
                if (response.isSuccessful) {
                    val user = response.body() ?: return

                    // Campos de texto: nunca borramos valores válidos con nulos o blancos
                    val (nombreBase, apellidoBase) = descomponerNombreCompleto(user.nombre)
                    if (nombreBase.isNotBlank()) vm.nombre = nombreBase
                    if (apellidoBase.isNotBlank()) vm.apellido = apellidoBase
                    if (!user.carrera.isNullOrBlank())   vm.carrera   = user.carrera
                    if (!user.bio.isNullOrBlank())       vm.bio       = user.bio
                    if (!user.fecha_nacimiento.isNullOrBlank())
                        vm.fechaNacimiento = normalizarFechaNacimientoDesdeApi(user.fecha_nacimiento)

                    // Género: el servidor ya lo devuelve mapeado ("Chico/Chica/Otro") desde /login
                    if (!user.genero.isNullOrBlank()) vm.genero = mapearGeneroDeOracle(user.genero)

                    // Preferencias
                    if (!user.que_busco.isNullOrBlank()) vm.queBusco = user.que_busco
                    if (!user.ciudad_residencia.isNullOrBlank()) vm.ciudadResidencia = user.ciudad_residencia
                    if (!user.ciudad_busqueda.isNullOrBlank())   vm.ciudadBusqueda = user.ciudad_busqueda

                    val inicio = user.rango_inicio?.toFloat() ?: vm.rangoEdadBusqueda.start
                    val fin    = user.rango_fin?.toFloat()    ?: vm.rangoEdadBusqueda.endInclusive
                    vm.rangoEdadBusqueda = inicio..fin

                    vm.isPremium = when (user.es_premium) {
                        "Sí" -> true
                        "No" -> false
                        else -> vm.isPremium
                    }

                    // Fotos: solo tocamos si Oracle devuelve URLs reales
                    val fotosDesdeApi = listOf(
                        user.foto1, user.foto2, user.foto3,
                        user.foto4, user.foto5, user.foto6
                    ).filterNotNull()
                        .filter { it.isNotBlank() && (it.startsWith("http") || it.startsWith("content")) }

                    if (fotosDesdeApi.isNotEmpty()) {
                        vm.fotosSelected.clear()
                        fotosDesdeApi.forEach { vm.fotosSelected.add(Uri.parse(it)) }
                        sharedPreferences.edit()
                            .putString("fotosUsuario", fotosDesdeApi.joinToString(","))
                            .apply()
                    }
                    // Si está vacío → no tocamos fotosSelected, se conservan las locales
                }
            }
            override fun onFailure(call: retrofit2.Call<Usuario>, t: Throwable) {
                // Sin red → los datos de SharedPreferences ya están cargados, no hay problema
            }
        })
    }

    Scaffold(bottomBar = { BarraNavegacionInferior(navController, currentRoute = "mi_perfil") }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).background(Color.White)) {
            Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                if (vm.fotosSelected.isNotEmpty()) AsyncImage(model = vm.fotosSelected[0], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(GrisSuave), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, Modifier.size(100.dp), AzulUCA) }

                if (vm.isPremium) Surface(modifier = Modifier.align(Alignment.TopEnd).padding(20.dp), color = NaranjaUCA, shape = RoundedCornerShape(20.dp)) { Text("PREMIUM ✨", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                
                    val edadCalculada = vm.obtenerEdad()
                    val textoEdad = if (edadCalculada > 0) edadCalculada.toString() else "00"

                    Text("${vm.nombre.ifBlank { "Nombre" }} ${vm.apellido}, $textoEdad", fontSize = 28.sp, fontWeight = FontWeight.Black, color = AzulOscuroUCA)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Verified, null, tint = AzulUCA, modifier = Modifier.size(24.dp))
                }
                Text(text = vm.carrera.ifBlank { "Carrera universitaria" }, fontSize = 18.sp, color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Text("Sobre mí", fontWeight = FontWeight.Bold, color = AzulUCA)
                Text(vm.bio.ifBlank { "No has escrito ninguna biografía todavía." }, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp), lineHeight = 22.sp)
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoCard(Icons.Default.Wc, vm.genero.ifBlank { "Género" }, Modifier.weight(1f))
                    InfoCard(Icons.Default.Search, "Busca: ${vm.queBusco.ifBlank { "Todos" }}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                InfoCard(Icons.Default.Cake, "Edad objetivo: de ${vm.rangoEdadBusqueda.start.roundToInt()} a ${vm.rangoEdadBusqueda.endInclusive.roundToInt()} años", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                InfoCard(Icons.Default.LocationOn, "Reside en: ${vm.ciudadResidencia.ifBlank { "Cádiz" }}", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(32.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (vm.isPremium) {
                        OutlinedButton(onClick = { mostrarDialogoCancelarPremium = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), border = BorderStroke(1.dp, NaranjaUCA)) {
                            Icon(Icons.Default.Cancel, null, tint = NaranjaUCA)
                            Spacer(Modifier.width(8.dp))
                            Text("CANCELAR SUSCRIPCIÓN PREMIUM", color = NaranjaUCA)
                        }
                    } else {
                        Button(onClick = { navController.navigate("premium") }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(NaranjaUCA)) {
                            Icon(Icons.Default.Stars, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("HACERSE PREMIUM ✨", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(onClick = { navController.navigate("perfil_setup") }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), border = BorderStroke(1.dp, AzulUCA)) {
                        Icon(Icons.Default.Edit, null, tint = AzulUCA); Spacer(Modifier.width(8.dp)); Text("EDITAR DATOS", color = AzulUCA)
                    }
                    OutlinedButton(onClick = { mostrarDialogoCerrarSesion = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), border = BorderStroke(1.dp, Color.Gray)) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.DarkGray); Spacer(Modifier.width(8.dp)); Text("CERRAR SESIÓN", color = Color.DarkGray)
                    }
                    OutlinedButton(onClick = { mostrarDialogoEliminarCuenta = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp), border = BorderStroke(1.dp, Color.Red)) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red); Spacer(Modifier.width(8.dp)); Text("ELIMINAR CUENTA", color = Color.Red)
                    }
                }
            }
        }

        if (mostrarDialogoCancelarPremium) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoCancelarPremium = false },
                title = { Text("Cancelar Premium", fontWeight = FontWeight.Bold, color = NaranjaUCA) },
                text = { Text("¿Seguro que quieres cancelar tu suscripción? Perderás los superlikes ilimitados.") },
                confirmButton = {
                    TextButton(onClick = {
                        mostrarDialogoCancelarPremium = false
                        vm.isPremium = false
                        sharedPreferences.edit().putBoolean("isPremiumUsuario", false).apply()
                        Toast.makeText(context, "Suscripción cancelada", Toast.LENGTH_SHORT).show()
                    }) { Text("Sí, cancelar", color = Color.Red, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { mostrarDialogoCancelarPremium = false }) { Text("Mantener Premium", color = AzulUCA) } }
            )
        }

        if (mostrarDialogoCerrarSesion) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoCerrarSesion = false },
                title = { Text("Cerrar sesión", fontWeight = FontWeight.Bold, color = AzulOscuroUCA) },
                text = { Text("¿Estás seguro de que quieres cerrar sesión?") },
                confirmButton = {
                    TextButton(onClick = {
                        mostrarDialogoCerrarSesion = false

                        // 1. Limpiamos el ViewModel
                        vm.limpiarDatosParaNuevaSesion()

                        // 2. Borramos TODO de la memoria del teléfono
                        sharedPreferences.edit().clear().apply()

                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true } // Esto borra el historial de navegación
                        }
                    }) { Text("Sí, cerrar sesión", color = Color.Red, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { mostrarDialogoCerrarSesion = false }) { Text("Cancelar", color = AzulUCA) } }
            )
        }

        if (mostrarDialogoEliminarCuenta) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoEliminarCuenta = false },
                title = { Text("¿Eliminar cuenta definitivamente?", fontWeight = FontWeight.Bold) },
                text = { Text("Esta acción no se puede deshacer. Perderás todos tus matches y mensajes de la base de datos de ESILigue.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            mostrarDialogoEliminarCuenta = false
                            RetrofitClient.instance.eliminarUsuario(vm.email).enqueue(object : retrofit2.Callback<Void> {
                                override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                                    if (response.isSuccessful) {
                                    
                                        vm.limpiarDatosParaNuevaSesion()
                                        sharedPreferences.edit().clear().apply()

                                        Toast.makeText(context, "Cuenta eliminada de Oracle", Toast.LENGTH_SHORT).show()

                                        // Navegamos y matamos todo el historial
                                        navController.navigate("auth") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    } else {
                                        Toast.makeText(context, "Error al borrar: ${response.code()}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                                    Toast.makeText(context, "Error de conexión con el servidor", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                    ) {
                        Text("SÍ, ELIMINAR", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogoEliminarCuenta = false }) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDatosPersonales(navController: NavController, vm: PerfilViewModel) {
    // Estados para controlar los desplegables
    var expCarrera by remember { mutableStateOf(false) }
    var expResidencia by remember { mutableStateOf(false) }
    var expBusqueda by remember { mutableStateOf(false) }

    val carreras = listOf("Ing. Informática", "Ing. Aeroespacial", "Ing. Mecánica", "Ing. Diseño")

    // --- 1. LISTA PARA "MI CIUDAD" (Donde vive el usuario - Sin Provincia) ---
    val ciudadesResidencia = listOf(
        "Cádiz (Ciudad)",
        "Jerez de la Frontera",
        "Algeciras",
        "San Fernando",
        "El Puerto de Santa María",
        "Chiclana",
        "Sanlúcar",
        "La Línea",
        "Puerto Real",
        "Arcos",
        "Rota",
        "Conil",
        "Tarifa",
        "Ubrique"
    )

    // --- 2. LISTA PARA "BUSCAR EN" (Incluye la opción global) ---
    val ciudadesBusqueda = listOf("Cádiz (Provincia)") + ciudadesResidencia

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AzulOscuroUCA, AzulUCA)))) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CenterAlignedTopAppBar(
                title = { Text("Tus Datos", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth().wrapContentHeight(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(Color.White)) {
                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        CustomTextField(vm.nombre, { vm.nombre = it }, "Nombre", Icons.Default.Person)
                        CustomTextField(vm.apellido, { vm.apellido = it }, "Primer Apellido", Icons.Default.Badge)

                        // --- FECHA DE NACIMIENTO (Sin cambios) ---
                        val dateVisualTransformation = remember {
                            object : androidx.compose.ui.text.input.VisualTransformation {
                                override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
                                    val trimmed = if (text.text.length >= 8) text.text.substring(0..7) else text.text
                                    var out = ""
                                    for (i in trimmed.indices) {
                                        out += trimmed[i]
                                        if (i == 1 || i == 3) out += "/"
                                    }
                                    val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
                                        override fun originalToTransformed(offset: Int): Int {
                                            if (offset <= 1) return offset; if (offset <= 3) return offset + 1; if (offset <= 8) return offset + 2; return out.length
                                        }
                                        override fun transformedToOriginal(offset: Int): Int {
                                            if (offset <= 2) return offset; if (offset <= 5) return offset - 1; if (offset <= 10) return offset - 2; return trimmed.length
                                        }
                                    }
                                    return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(out), offsetMapping)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = vm.fechaNacimiento,
                            onValueChange = { input -> vm.fechaNacimiento = input.filter { it.isDigit() }.take(8) },
                            label = { Text("Fecha de Nacimiento (DD/MM/AAAA)") },
                            leadingIcon = { Icon(Icons.Default.Cake, null, tint = AzulUCA) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            isError = vm.fechaNacimiento.length == 8 && !vm.isFechaNacimientoValid,
                            visualTransformation = dateVisualTransformation,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )

                        // --- GÉNERO ---
                        Text("Yo soy:", color = AzulUCA, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Chico", "Chica", "Otro").forEach { gen ->
                                FilterChip(
                                    selected = (vm.genero ?: "") == gen, // Añade el ?: ""
                                    onClick = { vm.genero = gen },
                                    label = { Text(gen) }
                                )
                            }
                        }

                        // --- CIUDAD DE RESIDENCIA (Donde vive) ---
                        Text("Mi ciudad:", color = AzulUCA, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        ExposedDropdownMenuBox(expResidencia, { expResidencia = !expResidencia }) {
                            OutlinedTextField(
                                value = vm.ciudadResidencia,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("¿Dónde vives?") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = AzulUCA) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expResidencia) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )
                            ExposedDropdownMenu(expResidencia, { expResidencia = false }) {
                                // USAMOS LA LISTA SIN PROVINCIA
                                ciudadesResidencia.forEach { c ->
                                    DropdownMenuItem(text = { Text(c) }, onClick = { vm.ciudadResidencia = c; expResidencia = false })
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), thickness = 0.5.dp, color = Color.LightGray)

                        // --- PREFERENCIAS DE EDAD Y GÉNERO ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Busco entre:", color = NaranjaUCA, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            // ESTA ES LA LÍNEA QUE FALTABA PARA VER LOS NÚMEROS
                            Text(
                                text = "${vm.rangoEdadBusqueda.start.roundToInt()} y ${vm.rangoEdadBusqueda.endInclusive.roundToInt()} años",
                                color = AzulUCA,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        RangeSlider(
                            value = vm.rangoEdadBusqueda,
                            onValueChange = { nuevoRango ->
                                if (nuevoRango.start <= nuevoRango.endInclusive) {
                                    vm.rangoEdadBusqueda = nuevoRango
                                }
                            },
                            valueRange = 18f..49f,
                            colors = SliderDefaults.colors(thumbColor = NaranjaUCA, activeTrackColor = NaranjaUCA)
                        )

                        Text("Interesado en:", color = NaranjaUCA, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Chicos", "Chicas", "Todos").forEach { qb ->
                                FilterChip(
                                    selected = vm.queBusco == qb,
                                    onClick = { vm.queBusco = qb },
                                    label = { Text(qb) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NaranjaUCA, selectedLabelColor = Color.White)
                                )
                            }
                        }

                        // --- CIUDAD DE BÚSQUEDA (Donde busca) ---
                        Text("Buscar gente en:", color = NaranjaUCA, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        ExposedDropdownMenuBox(expBusqueda, { expBusqueda = !expBusqueda }) {
                            OutlinedTextField(
                                value = vm.ciudadBusqueda,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Filtrar por ciudad") },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = NaranjaUCA) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expBusqueda) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )
                            ExposedDropdownMenu(expBusqueda, { expBusqueda = false }) {
                                // USAMOS LA LISTA QUE SÍ TIENE PROVINCIA
                                ciudadesBusqueda.forEach { c ->
                                    DropdownMenuItem(text = { Text(c) }, onClick = { vm.ciudadBusqueda = c; expBusqueda = false })
                                }
                            }
                        }

                        // --- CARRERA ---
                        ExposedDropdownMenuBox(expCarrera, { expCarrera = !expCarrera }) {
                            OutlinedTextField(value = vm.carrera, onValueChange = {}, readOnly = true, label = { Text("¿Qué carrera estudias?") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expCarrera) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                            ExposedDropdownMenu(expCarrera, { expCarrera = false }) { carreras.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { vm.carrera = c; expCarrera = false }) } }
                        }

                        Button(onClick = { navController.navigate("fotos_setup") }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = vm.canContinueDatos, shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(AzulUCA)) { Text("CONTINUAR") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaSubirFotos(navController: NavController, vm: PerfilViewModel) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("ESILiguePrefs", Context.MODE_PRIVATE)
    var showSourceDialog by remember { mutableStateOf(false) }
    var fotoUriTemporal by remember { mutableStateOf<Uri?>(null) }
    var fotoAEliminar by remember { mutableStateOf<Uri?>(null) }

    // Usamos el selector clásico de Android para evitar que el emulador colapse
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && vm.fotosSelected.size < 6) {
            vm.fotosSelected.add(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        if (exito && fotoUriTemporal != null) {
            if (vm.fotosSelected.size < 6) {
                vm.fotosSelected.add(fotoUriTemporal!!)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AzulOscuroUCA, AzulUCA)))) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CenterAlignedTopAppBar(
                title = { Text("Finalizar Perfil", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )

            Text(
                text = "Sube entre 3 y 6 fotos. (${vm.fotosSelected.size}/6)",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                textAlign = TextAlign.Center
            )

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(vm.fotosSelected) { uri ->
                                Box(Modifier.aspectRatio(0.8f).clip(RoundedCornerShape(12.dp))) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { fotoAEliminar = uri },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            if (vm.fotosSelected.size < 6) {
                                item {
                                    Box(
                                        Modifier
                                            .aspectRatio(0.8f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(FondoGris)
                                            .clickable { showSourceDialog = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AddAPhoto, null, tint = AzulUCA)
                                    }
                                }
                            }
                        }

                        val isBioError = vm.bio.isNotEmpty() && vm.bio.length < 20
                        OutlinedTextField(
                            value = vm.bio,
                            onValueChange = { if (it.length <= 150) vm.bio = it },
                            placeholder = { Text("Escribe algo sobre ti... (min. 20 carac.)") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            shape = RoundedCornerShape(16.dp),
                            isError = isBioError,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AzulUCA,
                                errorBorderColor = Color.Red
                            ),
                            supportingText = {
                                Text(
                                    text = if (isBioError) "Mínimo 20 caracteres (${vm.bio.length}/150)" else "${vm.bio.length}/150",
                                    color = if (isBioError) Color.Red else Color.Gray
                                )
                            }
                        )

                        Button(
                            onClick = {
                                val nuevoUsuario = Usuario(
                                    id_usuario = if (vm.miIdReal != 0L) vm.miIdReal else null,
                                    nombre = listOf(vm.nombre.trim(), vm.apellido.trim()).filter { it.isNotBlank() }.joinToString(" ").trim(),
                                    correo = vm.email,
                                    contrasena = vm.password,
                                    genero = vm.genero,
                                    es_premium = if (vm.isPremium) "Sí" else "No",
                                    apellido = vm.apellido,
                                    edad = vm.obtenerEdad(),
                                    fecha_nacimiento = if (vm.fechaNacimiento.length == 8) "${vm.fechaNacimiento.substring(0,2)}/${vm.fechaNacimiento.substring(2,4)}/${vm.fechaNacimiento.substring(4,8)}" else vm.fechaNacimiento,
                                    carrera = vm.carrera,
                                    bio = vm.bio,
                                    que_busco = vm.queBusco,
                                    rango_inicio = vm.rangoEdadBusqueda.start.toInt(),
                                    rango_fin = vm.rangoEdadBusqueda.endInclusive.toInt(),
                                    foto1 = vm.fotosSelected.getOrNull(0)?.toString() ?: "",
                                    foto2 = vm.fotosSelected.getOrNull(1)?.toString() ?: "",
                                    foto3 = vm.fotosSelected.getOrNull(2)?.toString() ?: "",
                                    foto4 = vm.fotosSelected.getOrNull(3)?.toString() ?: "",
                                    foto5 = vm.fotosSelected.getOrNull(4)?.toString() ?: "",
                                    foto6 = vm.fotosSelected.getOrNull(5)?.toString() ?: "",
                                    ciudad_residencia = vm.ciudadResidencia,
                                    ciudad_busqueda = vm.ciudadBusqueda,
                                )

                                RetrofitClient.instance.registrarUsuario(nuevoUsuario)
                                    .enqueue(object : retrofit2.Callback<Usuario> {
                                        override fun onResponse(call: retrofit2.Call<Usuario>, response: retrofit2.Response<Usuario>) {
                                            if (response.isSuccessful) {
                                                val usuarioCreado = response.body()
                                                val idAsignado = usuarioCreado?.id_usuario ?: 0L
                                                vm.miIdReal = idAsignado

                                                val fotosString = vm.fotosSelected.joinToString(",") { it.toString() }

                                                sharedPreferences.edit().apply {
                                                    putBoolean("isLoggedIn", true)
                                                    putLong("idUsuarioReal", idAsignado)
                                                    putString("emailRegistrado", vm.email)
                                                    putString("passwordUsuario", vm.password)
                                                    putString("nombreUsuario", listOf(vm.nombre.trim(), vm.apellido.trim()).filter { it.isNotBlank() }.joinToString(" ").trim())
                                                    putString("fotosUsuario", fotosString)
                                                    putString("apellidoUsuario", vm.apellido)
                                                    putString("fechaNacimientoUsuario", vm.fechaNacimiento)
                                                    putString("edadUsuario", vm.obtenerEdad().toString())
                                                    putString("carreraUsuario", vm.carrera)
                                                    putString("bioUsuario", vm.bio)
                                                    putString("generoUsuario", vm.genero)
                                                    putString("queBuscoUsuario", vm.queBusco)
                                                    putFloat("rangoEdadInicio", vm.rangoEdadBusqueda.start)
                                                    putFloat("rangoEdadFin", vm.rangoEdadBusqueda.endInclusive)
                                                    apply()
                                                }
                                                navController.navigate("premium") {
                                                    popUpTo("auth") { inclusive = true }
                                                }
                                            } else {
                                                Toast.makeText(context, "Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        override fun onFailure(call: retrofit2.Call<Usuario>, t: Throwable) {
                                            Toast.makeText(context, "Sin conexión: ${t.message}", Toast.LENGTH_LONG).show()
                                        }
                                    })
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = vm.canFinishFotos,
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(NaranjaUCA)
                        ) {
                            Text("¡LISTO!", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showSourceDialog) {
            AlertDialog(
                onDismissRequest = { showSourceDialog = false },
                title = { Text("Añadir Foto", fontWeight = FontWeight.Bold, color = AzulUCA) },
                confirmButton = {},
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val photoFile = File.createTempFile("temp_capture_", ".jpg", context.cacheDir)
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        photoFile
                                    )
                                    fotoUriTemporal = uri
                                    cameraLauncher.launch(uri)
                                    showSourceDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Configura el FileProvider para usar la cámara", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cámara")
                        }

                        Button(
                            onClick = {
                                try {
                                    // Aquí lanzamos el selector que NO rompe el emulador
                                    galleryLauncher.launch("image/*")
                                    showSourceDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al abrir la galería", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(AzulUCA)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Elegir de la Galería")
                        }
                    }
                }
            )
        }

        if (fotoAEliminar != null) {
            AlertDialog(
                onDismissRequest = { fotoAEliminar = null },
                title = { Text("Eliminar foto") },
                text = { Text("¿Quitar esta foto del perfil?") },
                confirmButton = {
                    TextButton(onClick = { vm.fotosSelected.remove(fotoAEliminar); fotoAEliminar = null }) {
                        Text("Eliminar", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { fotoAEliminar = null }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
fun PantallaAuth(navController: NavController, vm: PerfilViewModel) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("ESILiguePrefs", Context.MODE_PRIVATE)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AzulOscuroUCA, AzulUCA))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(id = R.drawable.logo_esiligue), null, Modifier.size(120.dp))
            Spacer(Modifier.height(16.dp))
            Text("ESILigue", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(30.dp))

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(Color.White)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        if (vm.isLogin) "Inicia Sesión" else "Crea tu cuenta",
                        fontWeight = FontWeight.Bold,
                        color = AzulUCA,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))

                    CustomTextField(
                        vm.email,
                        { vm.email = it },
                        "Correo UCA",
                        Icons.Default.Email,
                        isError = vm.email.isNotEmpty() && !vm.isEmailValid
                    )
                    Spacer(Modifier.height(16.dp))

                    CustomTextField(
                        vm.password,
                        { vm.password = it },
                        "Contraseña (min. 6)",
                        Icons.Default.Lock,
                        isPassword = true,
                        isError = vm.password.isNotEmpty() && !vm.isPasswordLengthValid
                    )

                    AnimatedVisibility(visible = !vm.isLogin) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            CustomTextField(
                                vm.confirmPassword,
                                { vm.confirmPassword = it },
                                "Confirmar Contraseña",
                                Icons.Default.CheckCircle,
                                isPassword = true,
                                isError = vm.confirmPassword.isNotEmpty() && !vm.passwordsMatch
                            )
                        }
                    }

                    Spacer(Modifier.height(30.dp))

                    Button(
                        onClick = {
                            if (vm.isLogin) {
                                // 1. Creamos el objeto solo con lo necesario para loguearse
                                val loginRequest = Usuario(correo = vm.email, contrasena = vm.password)

                                // 2. Llamamos al nuevo método de Retrofit
                                RetrofitClient.instance.login(loginRequest).enqueue(object : retrofit2.Callback<Usuario> {
                                    override fun onResponse(call: retrofit2.Call<Usuario>, response: retrofit2.Response<Usuario>) {
                                        // Si el servidor nos devuelve código 200 y el cuerpo no está vacío, la contraseña es correcta
                                        if (response.isSuccessful && response.body() != null) {
                                            val usuarioValido = response.body()!!

                                            val idRecuperado = usuarioValido.id_usuario ?: 0L
                                            vm.miIdReal = idRecuperado

                                            val (nombreBase, apellidoBase) = descomponerNombreCompleto(usuarioValido.nombre)
                                            if (nombreBase.isNotBlank()) vm.nombre = nombreBase
                                            if (apellidoBase.isNotBlank()) vm.apellido = apellidoBase
                                            if (!usuarioValido.bio.isNullOrBlank()) vm.bio = usuarioValido.bio
                                            if (!usuarioValido.carrera.isNullOrBlank()) vm.carrera = usuarioValido.carrera
                                            if (!usuarioValido.genero.isNullOrBlank()) vm.genero = mapearGeneroDeOracle(usuarioValido.genero)

                                            // Cargamos la fecha de nacimiento en lugar de la edad cruda
                                            vm.fechaNacimiento = normalizarFechaNacimientoDesdeApi(usuarioValido.fecha_nacimiento)

                                            if (!usuarioValido.que_busco.isNullOrBlank()) vm.queBusco = usuarioValido.que_busco
                                            val inicio = usuarioValido.rango_inicio?.toFloat() ?: vm.rangoEdadBusqueda.start
                                            val fin = usuarioValido.rango_fin?.toFloat() ?: vm.rangoEdadBusqueda.endInclusive
                                            vm.rangoEdadBusqueda = inicio..fin

                                            if (!usuarioValido.ciudad_residencia.isNullOrBlank()) vm.ciudadResidencia = usuarioValido.ciudad_residencia
                                            if (!usuarioValido.ciudad_busqueda.isNullOrBlank()) vm.ciudadBusqueda = usuarioValido.ciudad_busqueda

                                            vm.fotosSelected.clear()

                                            val fotosDesdeOracle = listOf(
                                                usuarioValido.foto1, usuarioValido.foto2, usuarioValido.foto3,
                                                usuarioValido.foto4, usuarioValido.foto5, usuarioValido.foto6
                                            )

                                            fotosDesdeOracle.forEach { fotoUrl ->
                                                if (!fotoUrl.isNullOrEmpty()) {
                                                    vm.fotosSelected.add(Uri.parse(fotoUrl))
                                                }
                                            }

                                            sharedPreferences.edit().apply {
                                                putBoolean("isLoggedIn", true)
                                                putLong("idUsuarioReal", idRecuperado)
                                                putString("emailRegistrado", usuarioValido.correo ?: vm.email)
                                                putString("passwordUsuario", vm.password) // Guardamos la plana para autologin si hace falta

                                                val fotosString = fotosDesdeOracle.filter { !it.isNullOrEmpty() }.joinToString(",")
                                                putString("fotosUsuario", fotosString)

                                                putString("nombreUsuario", listOf(nombreBase, apellidoBase).filter { it.isNotBlank() }.joinToString(" ").trim())
                                                putString("apellidoUsuario", apellidoBase)
                                                putString("bioUsuario", usuarioValido.bio)
                                                putString("carreraUsuario", usuarioValido.carrera)
                                                putString("generoUsuario", usuarioValido.genero)

                                                putString("fechaNacimientoUsuario", usuarioValido.fecha_nacimiento ?: "")
                                                putString("edadUsuario", usuarioValido.edad?.toString() ?: "")

                                                putString("queBuscoUsuario", usuarioValido.que_busco ?: "Todos")
                                                putFloat("rangoEdadInicio", usuarioValido.rango_inicio?.toFloat() ?: 18f)
                                                putFloat("rangoEdadFin", usuarioValido.rango_fin?.toFloat() ?: 49f)

                                        
                                                putString("ciudadResidenciaUsuario", vm.ciudadResidencia)
                                                putString("ciudadBusquedaUsuario", vm.ciudadBusqueda)

                                                apply()
                                            }

                                            navController.navigate("swipe") {
                                                popUpTo("auth") { inclusive = true }
                                            }
                                        } else {
                                            // Si body es null o el status no es 200, la contraseña estaba mal
                                            Toast.makeText(context, "Correo o contraseña incorrectos", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    override fun onFailure(call: retrofit2.Call<Usuario>, t: Throwable) {
                                        Toast.makeText(context, "No hay conexión con la Base de Datos", Toast.LENGTH_LONG).show()
                                    }
                                })
                            } else {
                                // AQUÍ DEBES DEJAR TU CÓDIGO ORIGINAL DEL ELSE (El de comprobarCorreo para registrarse)
                                RetrofitClient.instance.comprobarCorreo(vm.email).enqueue(object : retrofit2.Callback<Boolean> {
                                    override fun onResponse(call: retrofit2.Call<Boolean>, response: retrofit2.Response<Boolean>) {
                                        if (response.isSuccessful) {
                                            val existe = response.body() ?: false
                                            if (existe) {
                                                Toast.makeText(context, "Este correo ya está registrado", Toast.LENGTH_LONG).show()
                                            } else {
                                                navController.navigate("perfil_setup")
                                            }
                                        } else {
                                            Toast.makeText(context, "Error al verificar correo", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    override fun onFailure(call: retrofit2.Call<Boolean>, t: Throwable) {
                                        Toast.makeText(context, "Error de red", Toast.LENGTH_SHORT).show()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = vm.canSubmitAuth,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(AzulUCA)
                    ) {
                        Text(if (vm.isLogin) "ENTRAR" else "CONTINUAR")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { vm.isLogin = !vm.isLogin }) {
                Text(
                    if (vm.isLogin) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Entra",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PantallaPremium(navController: NavController, vm: PerfilViewModel) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("ESILiguePrefs", Context.MODE_PRIVATE)
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color.Black))), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Default.Stars, null, Modifier.size(80.dp), NaranjaUCA); Text("ESILigue Premium", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(Color(0xFF252525))) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("🔥 Swipes Ilimitados", color = Color.White); Text("👀 Mira quién te dio Like", color = Color.White); Text("🏆 Superlikes Activados", color = Color.White) }
            }
            Button(onClick = { vm.isPremium = true; sharedPreferences.edit().putBoolean("isPremiumUsuario", true).apply(); Toast.makeText(context, "¡Bienvenido a Premium!", Toast.LENGTH_SHORT).show(); navController.navigate("swipe") { popUpTo("swipe") { inclusive = true } } }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(NaranjaUCA)) { Text("ACTIVAR PREMIUM ✨") }
            TextButton(onClick = { navController.navigate("swipe") }) { Text("Cerrar", color = Color.White.copy(0.6f)) }
        }
    }
}



data class Usuario(
    val id_usuario: Long? = null,
    val nombre: String? = null,
    val correo: String? = null,
    val contrasena: String? = null,
    val genero: String? = null,
    val es_premium: String? = null,
    val apellido: String? = null,
    val edad: Int? = null,
    val fecha_nacimiento: String? = null,
    val carrera: String? = null,
    val bio: String? = null,
    val que_busco: String? = null,
    val rango_inicio: Int? = null,
    val rango_fin: Int? = null,
    val foto1: String? = null,
    val foto2: String? = null,
    val foto3: String? = null,
    val foto4: String? = null,
    val foto5: String? = null,
    val foto6: String? = null,
    val ciudad_residencia: String? = null,
    val ciudad_busqueda: String? = null
)

data class Swipe(
    val id_swipe: Long? = null,
    val id_origen: Long,
    val id_destino: Long,
    val tipo_swipe: String
)

interface UsuarioApiService {

    @GET("api/usuarios/descubrir/{id}")
    fun obtenerUsuariosParaDescubrir(@Path("id") id: Long): Call<List<Usuario>>

    @GET("api/usuarios")
    fun obtenerTodos(): Call<List<Usuario>>

    @POST("api/usuarios")
    fun registrarUsuario(@Body usuario: Usuario): Call<Usuario>

    // --- BLOQUE DE SWIPES Y MATCHES ---
    @POST("api/swipes/registrar")
    fun registrarSwipe(@Body swipe: Swipe): Call<String>

    @GET("api/usuarios/recibidos/{id}")
    fun obtenerLikesRecibidos(@Path("id") id: Long): Call<List<Usuario>>

    @GET("api/usuarios/matches/{id}")
    fun obtenerMatches(@Path("id") id: Long): Call<List<Usuario>>

    // --- BLOQUE DE SEGURIDAD Y CUENTA ---

    @POST("api/usuarios/login")
    fun login(@Body usuario: Usuario): Call<Usuario>

    @GET("api/usuarios/existe/{correo}")
    fun comprobarCorreo(@Path("correo") correo: String): Call<Boolean>

    @DELETE("api/usuarios/{correo}")
    fun eliminarUsuario(@Path("correo") correo: String): Call<Void>

    @GET("api/usuarios/fotos/{id}")
    fun obtenerFotosDeUsuario(@Path("id") id: Long): Call<List<String>>
}

object RetrofitClient {
    private const val BASE_URL = BuildConfig.API_BASE_URL

    val instance: UsuarioApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(UsuarioApiService::class.java)
    }
}