package ar.jornadas

import android.app.Application
import android.content.Context
import androidx.room.Room
import ar.jornadas.data.*
import ar.jornadas.lab.ServicioLocal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Vive lo mismo que el proceso: una sola instancia de Servicios por sesión. */
class JornadasApp : Application() {
    val servicios by lazy { Servicios(this) }
}

class Servicios(context: Context) {
    val mesa = ServicioLocal(context)
    private val archivo = Room.databaseBuilder(context, Archivo::class.java, "cartelera.db")
        .addMigrations(MIGRACION_1_2).build()
    private val api = Retrofit.Builder().baseUrl("https://programa.invalid/")
        .client(OkHttpClient.Builder().addInterceptor(mesa).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ProgramaApi::class.java)
    /** Alcance de la app: rotar o cerrar la Activity no cancela una descarga en curso. */
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val agenda = AgendaRepository(api, archivo.cartelera(), alcance)
}
