package no.nav.syfo.syketilfelle.domain

import no.nav.syfo.kafka.consumers.syketilfelle.domain.Syketilfelledag
import java.time.temporal.ChronoUnit

class Tidslinje(private val syketilfellebiter: Syketilfellebiter) {
    private val tidslinjeliste: List<Syketilfelledag>

    init {
        tidslinjeliste = genererTidslinje()
    }

    fun tidslinjeSomListe(): List<Syketilfelledag> {
        return tidslinjeliste
    }

    private fun genererTidslinje(): List<Syketilfelledag> {
        require(syketilfellebiter.biter.isNotEmpty())

        val tidligsteFom = syketilfellebiter.finnTidligsteFom()
        val sensesteTom = syketilfellebiter.finnSenesteTom()

        return (0..ChronoUnit.DAYS.between(tidligsteFom, sensesteTom))
            .map(tidligsteFom::plusDays)
            .map(syketilfellebiter::tilSyketilfelleIntradag)
            .map(SyketilfelleIntradag::velgSyketilfelledag)
    }
}
