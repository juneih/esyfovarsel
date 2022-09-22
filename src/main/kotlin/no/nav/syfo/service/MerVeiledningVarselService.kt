package no.nav.syfo.service

import no.nav.syfo.*
import no.nav.syfo.access.domain.UserAccessStatus
import no.nav.syfo.kafka.consumers.varselbus.domain.ArbeidstakerHendelse
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.DittSykefravaerVarsel
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.OpprettMelding
import no.nav.syfo.kafka.producers.dittsykefravaer.domain.Variant
import no.nav.syfo.syketilfelle.SyketilfellebitService
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

const val DITT_SYKEFRAVAER_HENDELSE_TYPE_MER_VEILEDNING = "ESYFOVARSEL_MER_VEILEDNING"
class MerVeiledningVarselService(
    val senderFacade: SenderFacade,
    val syketilfellebitService: SyketilfellebitService,
    val urlEnv: UrlEnv
) {
    private val log = LoggerFactory.getLogger("no.nav.syfo.service.MerVeiledningVarselService")
    fun sendVarselTilArbeidstaker(
        arbeidstakerHendelse: ArbeidstakerHendelse,
        planlagtVarselUuid: String,
        userAccessStatus: UserAccessStatus
    ) {
        if (userAccessStatus.canUserBeDigitallyNotified) {
            sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse)
        } else {
            sendBrevVarselTilArbeidstaker(arbeidstakerHendelse.arbeidstakerFnr, planlagtVarselUuid, arbeidstakerHendelse)
        }
        sendOppgaveTilDittSykefravaer(arbeidstakerHendelse.arbeidstakerFnr, planlagtVarselUuid, arbeidstakerHendelse)
    }

    private fun sendDigitaltVarselTilArbeidstaker(arbeidstakerHendelse: ArbeidstakerHendelse) {
        val uuid = "${UUID.randomUUID()}"
        val fnr = arbeidstakerHendelse.arbeidstakerFnr
        val url = URL(urlEnv.baseUrlSykInfo + BRUKERNOTIFIKASJONER_MER_VEILEDNING_URL)

        senderFacade.sendTilBrukernotifikasjoner(
            uuid,
            fnr,
            BRUKERNOTIFIKASJONER_MER_VEILEDNING_MESSAGE_TEXT,
            url,
            arbeidstakerHendelse
        )
    }

    private fun sendBrevVarselTilArbeidstaker(
        fnr: String,
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse
    ) {
        try {
            senderFacade.sendBrevTilFysiskPrint(fnr, uuid, arbeidstakerHendelse)
        } catch (e: RuntimeException) {
            log.info("Feil i sending av fysisk brev: ${e.message}")
        }
    }

    private fun sendOppgaveTilDittSykefravaer(
        fnr: String,
        uuid: String,
        arbeidstakerHendelse: ArbeidstakerHendelse
    ) {
        val syketilfelleEndDate = syketilfellebitService.sisteDagISyketilfelle(fnr)
        if (syketilfelleEndDate == null) {
            log.error("Syketilfelle finnes ikke for varsel med UUID: $uuid. Sender ikke oppgave")
            throw RuntimeException("Uventet null-verdi ved henting av syketilfelle")
        }
        val melding = DittSykefravaerMelding(
            OpprettMelding(
                DITT_SYKEFRAVAER_MER_VEILEDNING_MESSAGE_TEXT,
                DITT_SYKEFRAVAER_MER_VEILEDNING_URL,
                Variant.info,
                true,
                DITT_SYKEFRAVAER_HENDELSE_TYPE_MER_VEILEDNING,
                syketilfelleEndDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            ),
            null,
            fnr
        )
        senderFacade.sendTilDittSykefravaer(
            arbeidstakerHendelse,
            DittSykefravaerVarsel(
                uuid,
                melding
            )
        )
    }
}