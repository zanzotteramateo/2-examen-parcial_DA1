package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import ar.jornadas.data.Charla
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Lo que hay para mostrar, leído siempre de la edición guardada en disco. */
sealed interface Contenido {
    data object Leyendo : Contenido
    data object SinEdicion : Contenido
    data class Programa(val charlas: List<Charla>) : Contenido
}

/** Estado de la descarga, aparte del contenido: una falla no borra lo que ya se ve. */
data class Descarga(val enCurso: Boolean = false, val fallo: Boolean = false)

class ProgramaModel(app: Application) : AndroidViewModel(app) {
    private val servicios = Servicios(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val contenido: StateFlow<Contenido> = repo.programa
        .map { if (it == null) Contenido.SinEdicion else Contenido.Programa(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Contenido.Leyendo)
    val dia = MutableStateFlow(0)
    private val _descarga = MutableStateFlow(Descarga())
    val descarga: StateFlow<Descarga> = _descarga.asStateFlow()

    fun recargar() {
        viewModelScope.launch {
            _descarga.update { it.copy(enCurso = true) }
            val fallo = try {
                repo.sincronizar()
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // red, HTTP o JSON inválido: se siguen usando los datos guardados
                true
            }
            _descarga.value = Descarga(enCurso = false, fallo = fallo)
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
