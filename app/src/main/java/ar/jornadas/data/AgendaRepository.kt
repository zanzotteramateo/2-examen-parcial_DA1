package ar.jornadas.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Estado de la descarga, aparte del contenido: una falla no borra lo que ya se ve. */
data class Descarga(val enCurso: Boolean = false, val fallo: Boolean = false)

class AgendaRepository(
    private val api: ProgramaApi,
    private val dao: CarteleraDao,
    private val alcance: CoroutineScope,
) {
    /** null = nunca se guardó una edición; lista (quizás vacía) = la última edición guardada. */
    val programa: Flow<List<Charla>?> =
        combine(dao.observarEdicion(), dao.observar()) { edicion, charlas -> if (edicion == null) null else charlas }

    private val _descarga = MutableStateFlow(Descarga())
    val descarga: StateFlow<Descarga> = _descarga.asStateFlow()

    // El repositorio vive lo mismo que el proceso (Servicios es único en JornadasApp).
    private val cargaAutomaticaHecha = AtomicBoolean(false)
    private val peticionEnCurso = AtomicBoolean(false)

    /** Una sola carga automática por proceso: recrear la Activity o el ViewModel no la repite. */
    fun cargarAlIniciar() {
        if (cargaAutomaticaHecha.compareAndSet(false, true)) actualizar()
    }

    /** Si ya hay una petición en curso, el toque se ignora (no se cancela ni se relanza). */
    fun actualizar() {
        if (!peticionEnCurso.compareAndSet(false, true)) return
        _descarga.update { it.copy(enCurso = true) }
        alcance.launch {
            var fallo = true
            try {
                sincronizar()
                fallo = false
            } catch (e: Exception) { // red, HTTP o JSON inválido: se siguen usando los datos guardados
                if (e is CancellationException) throw e
            } finally { // tras éxito o falla se puede volver a pedir
                _descarga.value = Descarga(enCurso = false, fallo = fallo)
                peticionEnCurso.set(false)
            }
        }
    }

    /** Lanza excepción si falla; en ese caso no toca lo guardado. */
    private suspend fun sincronizar() {
        val fichas = api.consultar().map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        dao.reemplazar(fichas)
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}
