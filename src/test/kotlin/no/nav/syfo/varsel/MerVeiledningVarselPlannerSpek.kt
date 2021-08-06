package no.nav.syfo.varsel


import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.consumer.SyfosyketilfelleConsumer
import no.nav.syfo.consumer.domain.*
import no.nav.syfo.db.domain.PPlanlagtVarsel
import no.nav.syfo.db.domain.PlanlagtVarsel
import no.nav.syfo.db.domain.VarselType
import no.nav.syfo.db.fetchPlanlagtVarselByFnr
import no.nav.syfo.db.storePlanlagtVarsel
import no.nav.syfo.kafka.oppfolgingstilfelle.domain.Oppfolgingstilfelle39Uker
import no.nav.syfo.testutil.EmbeddedDatabase
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.should
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object MerVeiledningVarselPlannerSpek : Spek({

    describe("Varsel39UkerSpek") {
        val embeddedDatabase by lazy { EmbeddedDatabase() }
        val syketilfelleConsumer = mockk<SyfosyketilfelleConsumer>()

        val merVeiledningVarselPlanner = MerVeiledningVarselPlanner(embeddedDatabase, syketilfelleConsumer)

        afterEachTest {
            embeddedDatabase.connection.dropData()
        }

        afterGroup {
            embeddedDatabase.stop()
        }

        it("Varsel blir planlagt når sykmeldingen strekker seg over 39 uker") {

            val fom = LocalDate.now().minusWeeks(38).minusDays(6)
            val tom = LocalDate.now().plusDays(1)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt(),
                fom,
                tom
            )


            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalHaEt39UkersVarsel()
            }
        }

        it("Varsel blir ikke planlagt når sykmeldingen ikke strekker seg over 39 uker") {
            val fom = LocalDate.now().minusWeeks(38)
            val tom = LocalDate.now().plusDays(6)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt(),
                fom,
                tom
            )

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)

                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)
                lagreteVarsler.skalIkkeHa39UkersVarsel()
            }
        }

        it("Varsel blir planlagt selv om arbeidstaker har vært sykmeldt over 39 uker allerede") {

            val fom = LocalDate.now().minusWeeks(40)
            val tom = LocalDate.now().plusWeeks(1)
            val utsendingsdato = fom.plusWeeks(39)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt(),
                fom,
                tom
            )

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)
                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

                lagreteVarsler.skalHaEt39UkersVarsel()
                lagreteVarsler.skalHaUtsendingPaDato(utsendingsdato)
            }
        }

        it("Tidligere usendt varsel er allerede planlagt og blir korrigert av nytt Oppfolgingstilfelle") {
            val fom = LocalDate.now()
            val tom = LocalDate.now().plusWeeks(41)
            val utsendingsdato = fom.plusWeeks(39)

            val fomTidligereVarsel = fom.plusWeeks(1)
            val utsendingsdatoTidligereVarsel = fomTidligereVarsel.plusWeeks(39)

            val tidligerePlanlagtVarsel = PlanlagtVarsel(
                arbeidstakerFnr1,
                arbeidstakerAktorId1,
                emptySet(),
                VarselType.MER_VEILEDNING,
                utsendingsdatoTidligereVarsel
            )

            embeddedDatabase.storePlanlagtVarsel(tidligerePlanlagtVarsel)

            val lagredeVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

            lagredeVarsler.skalHaEt39UkersVarsel()
            lagredeVarsler.skalHaUtsendingPaDato(utsendingsdatoTidligereVarsel)

            val oppfolgingstilfelle39Uker = Oppfolgingstilfelle39Uker(
                arbeidstakerAktorId1,
                FULL_AG_PERIODE,
                ChronoUnit.DAYS.between(fom, tom).toInt(),
                fom,
                tom
            )

            coEvery { syketilfelleConsumer.getOppfolgingstilfelle39Uker(any()) } returns oppfolgingstilfelle39Uker

            runBlocking {
                merVeiledningVarselPlanner.processOppfolgingstilfelle(arbeidstakerAktorId1, arbeidstakerFnr1)
                val lagreteVarsler = embeddedDatabase.fetchPlanlagtVarselByFnr(arbeidstakerFnr1)

                lagreteVarsler.skalHaEt39UkersVarsel()
                lagreteVarsler.skalHaUtsendingPaDato(utsendingsdato)
            }
        }

    }
})


private fun List<PPlanlagtVarsel>.skalHaEt39UkersVarsel() = this.should("Skal ha 39-ukersvarsel") {
    size == 1 && filter { it.type == VarselType.MER_VEILEDNING.name }.size == 1
}

private fun List<PPlanlagtVarsel>.skalIkkeHa39UkersVarsel() = this.should("Skal ikke ha 39-ukersvarsel") {
    size == 0
}

private fun List<PPlanlagtVarsel>.skalHaUtsendingPaDato(utsendingsdato: LocalDate) = this.should("Skal ha 39-ukersvarsel med utsendingsdato: $utsendingsdato") {
    filter { it.utsendingsdato == utsendingsdato }.size == 1
}