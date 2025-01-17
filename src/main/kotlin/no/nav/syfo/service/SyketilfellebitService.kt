package no.nav.syfo.syketilfelle

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.syketilfelle.domain.Tag.*
import no.nav.syfo.db.fetchSyketilfellebiterByFnr
import no.nav.syfo.kafka.consumers.syketilfelle.domain.Oppfolgingstilfelle39Uker
import no.nav.syfo.kafka.consumers.syketilfelle.domain.Syketilfelledag
import no.nav.syfo.syketilfelle.ListContainsPredicate.Companion.tagsSize
import no.nav.syfo.syketilfelle.domain.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

const val AntallDagerIArbeidsgiverPeriode = 16
private const val TjueseksUkerIAntallDager = 26 * 7

data class KOppfolgingstilfelle39Uker(
    val aktorId: String,
    val arbeidsgiverperiodeTotalt: Int,
    val antallSykefravaersDagerTotalt: Int,
    val fom: LocalDate,
    val tom: LocalDate
)
data class KOppfolgingstilfellePerson(
    val aktorId: String,
    val tidslinje: List<Syketilfelledag>,
    val sisteDagIArbeidsgiverperiode: Syketilfelledag,
    val antallBrukteDager: Int,
    val oppbruktArbeidsgiverperiode: Boolean,
    val utsendelsestidspunkt: LocalDateTime
)
fun KOppfolgingstilfelle39Uker.toOppfolgingstilfelle39Uker() = Oppfolgingstilfelle39Uker(
    this.aktorId,
    this.arbeidsgiverperiodeTotalt,
    this.antallSykefravaersDagerTotalt,
    this.fom,
    this.tom
)

class SyketilfellebitService(
    val database: DatabaseInterface
) {
    fun beregnKOppfolgingstilfelle39UkersVarsel(fnr: String): Oppfolgingstilfelle39Uker? =
        genererOppfolgingstilfelle(fnr)
            ?.filter { oppfolgingstilfelle -> oppfolgingstilfelle.dagerAvArbeidsgiverperiode > AntallDagerIArbeidsgiverPeriode }
            ?.let { slaaSammenTilfeller(fnr, it) }
            ?.toOppfolgingstilfelle39Uker()

    fun beregnKOppfolgingstilfelle(fnr: String): KOppfolgingstilfellePerson? =
        genererOppfolgingstilfelle(fnr)
            ?.last()
            ?.let { oppfolgingstilfelle ->
                KOppfolgingstilfellePerson(
                    fnr,
                    oppfolgingstilfelle.tidslinje,
                    oppfolgingstilfelle.sisteDagIArbeidsgiverperiode,
                    oppfolgingstilfelle.dagerAvArbeidsgiverperiode,
                    oppfolgingstilfelle.oppbruktArbeidsgiverperiode(),
                    LocalDateTime.now()
                )
            }

    fun genererOppfolgingstilfelle(fnr: String): List<Oppfolgingstilfelle>? {
        val biter = database.fetchSyketilfellebiterByFnr(fnr)
        return try {
            val tidslinje = Tidslinje(
                Syketilfellebiter(
                    prioriteringsliste = listOf(
                        SYKEPENGESOKNAD and SENDT and ARBEID_GJENNOPPTATT,
                        SYKEPENGESOKNAD and SENDT and KORRIGERT_ARBEIDSTID and BEHANDLINGSDAGER,
                        SYKEPENGESOKNAD and SENDT and KORRIGERT_ARBEIDSTID and FULL_AKTIVITET,
                        SYKEPENGESOKNAD and SENDT and KORRIGERT_ARBEIDSTID and (GRADERT_AKTIVITET or INGEN_AKTIVITET),
                        SYKEPENGESOKNAD and SENDT and (PERMISJON or FERIE),
                        SYKEPENGESOKNAD and SENDT and (EGENMELDING or PAPIRSYKMELDING or FRAVAR_FOR_SYKMELDING),
                        SYKEPENGESOKNAD and SENDT and tagsSize(2),
                        SYKEPENGESOKNAD and SENDT and BEHANDLINGSDAG,
                        SYKEPENGESOKNAD and SENDT and BEHANDLINGSDAGER,
                        SYKMELDING and (SENDT or BEKREFTET) and PERIODE and BEHANDLINGSDAGER,
                        SYKMELDING and (SENDT or BEKREFTET) and PERIODE and FULL_AKTIVITET,
                        SYKMELDING and (SENDT or BEKREFTET) and PERIODE and (GRADERT_AKTIVITET or INGEN_AKTIVITET),
                        SYKMELDING and (SENDT or BEKREFTET) and EGENMELDING,
                        SYKMELDING and BEKREFTET and ANNET_FRAVAR,
                        SYKMELDING and SENDT and PERIODE and REISETILSKUDD and UKJENT_AKTIVITET,
                        SYKMELDING and NY and PERIODE and BEHANDLINGSDAGER,
                        SYKMELDING and NY and PERIODE and FULL_AKTIVITET,
                        SYKMELDING and NY and PERIODE and (GRADERT_AKTIVITET or INGEN_AKTIVITET),
                        SYKMELDING and NY and PERIODE and REISETILSKUDD and UKJENT_AKTIVITET,
                    ),
                    biter = biter
                )
            )

            grupperIOppfolgingstilfeller(
                tidslinje
                    .tidslinjeSomListe()
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun sisteDagISyketilfelle(fnr: String): LocalDate? =
        beregnKOppfolgingstilfelle39UkersVarsel(fnr)?.tom

    private fun slaaSammenTilfeller(
        fnr: String,
        oppfolgingstilfeller: List<Oppfolgingstilfelle>
    ): KOppfolgingstilfelle39Uker? {
        return oppfolgingstilfeller
            .toMutableList()
            .map { oppfolgingstilfelle ->
                KOppfolgingstilfelle39Uker(
                    fnr,
                    oppfolgingstilfelle.antallDagerAGPeriodeBrukt(),
                    oppfolgingstilfelle.antallSykedager(),
                    oppfolgingstilfelle.tidslinje.first().dag,
                    oppfolgingstilfelle.tidslinje.last().dag
                )
            }
            .sortedBy { it.fom }
            .reduceRightOrNull { tilfelle: KOppfolgingstilfelle39Uker, acc ->
                acc.slaaSammenMedForrige(tilfelle)
            }
    }

    private fun KOppfolgingstilfelle39Uker.slaaSammenMedForrige(forrigeTilfelle: KOppfolgingstilfelle39Uker): KOppfolgingstilfelle39Uker {
        if (ChronoUnit.DAYS.between(forrigeTilfelle.tom, this.fom.leggTilArbeidsgiverPeriode()) < TjueseksUkerIAntallDager) {
            return KOppfolgingstilfelle39Uker(
                this.aktorId,
                this.arbeidsgiverperiodeTotalt + forrigeTilfelle.arbeidsgiverperiodeTotalt,
                this.antallSykefravaersDagerTotalt + forrigeTilfelle.antallSykefravaersDagerTotalt,
                forrigeTilfelle.fom,
                this.tom
            )
        }
        return this
    }

    private fun LocalDate.leggTilArbeidsgiverPeriode() = plusDays(AntallDagerIArbeidsgiverPeriode.toLong())
}
