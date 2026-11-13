package ar.jornadas.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AgendaRepository(private val api: ProgramaApi, private val dao: CarteleraDao) {
    /** null = nunca se guardó una edición; lista (quizás vacía) = la última edición guardada. */
    val programa: Flow<List<Charla>?> =
        combine(dao.observarEdicion(), dao.observar()) { edicion, charlas -> if (edicion == null) null else charlas }

    /** Lanza excepción si falla; en ese caso no toca lo guardado. */
    suspend fun sincronizar() {
        val fichas = api.consultar().map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        dao.reemplazar(fichas)
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}
