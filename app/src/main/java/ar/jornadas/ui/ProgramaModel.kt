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

class ProgramaModel(
    private val estado: SavedStateHandle,
    private val repo: AgendaRepository,
    val mesa: ServicioLocal,
) : ViewModel() {
    val contenido: StateFlow<Contenido> = repo.programa
        .map { if (it == null) Contenido.SinEdicion else Contenido.Programa(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Contenido.Leyendo)
    val descarga: StateFlow<Descarga> = repo.descarga

    // Filtros en el estado guardado de la tarea: sobreviven a rotar y a que Android mate el
    // proceso, pero una tarea nueva arranca sin filtros (no van a disco ni a objetos estáticos).
    val dia: StateFlow<Int> = estado.getStateFlow("dia", 0)
    val tema: StateFlow<String> = estado.getStateFlow("tema", "Todos")
    fun elegirDia(dia: Int) { estado["dia"] = dia }
    fun elegirTema(tema: String) { estado["tema"] = tema }

    // El repositorio la deja pasar una sola vez por proceso: rotar no agrega peticiones.
    init { repo.cargarAlIniciar() }

    fun recargar() = repo.actualizar()

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val servicios = (this[APPLICATION_KEY] as JornadasApp).servicios
                ProgramaModel(createSavedStateHandle(), servicios.agenda, servicios.mesa)
            }
        }
    }
}
