package ar.jornadas.ui

import androidx.lifecycle.*
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.jornadas.JornadasApp
import ar.jornadas.data.AgendaRepository
import ar.jornadas.data.Charla
import ar.jornadas.data.Descarga
import ar.jornadas.lab.ServicioLocal
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Lo que hay para mostrar, leído siempre de la edición guardada en disco. */
sealed interface Contenido {
    data object Leyendo : Contenido
    data object SinEdicion : Contenido
    data class Programa(val charlas: List<Charla>) : Contenido
}

class ProgramaModel(private val repo: AgendaRepository, val mesa: ServicioLocal) : ViewModel() {
    val contenido: StateFlow<Contenido> = repo.programa
        .map { if (it == null) Contenido.SinEdicion else Contenido.Programa(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Contenido.Leyendo)
    val dia = MutableStateFlow(0)
    val descarga: StateFlow<Descarga> = repo.descarga

    // El repositorio la deja pasar una sola vez por proceso: rotar no agrega peticiones.
    init { repo.cargarAlIniciar() }

    fun recargar() = repo.actualizar()

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val servicios = (this[APPLICATION_KEY] as JornadasApp).servicios
                ProgramaModel(servicios.agenda, servicios.mesa)
            }
        }
    }
}
