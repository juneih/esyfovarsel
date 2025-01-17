package no.nav.syfo.utils

import io.ktor.application.*
import io.ktor.http.*
import no.nav.syfo.domain.PersonIdent

fun ApplicationCall.getPersonIdent(): PersonIdent? =
    this.request.headers[NAV_PERSONIDENT_HEADER]?.let { PersonIdent(it) }

fun ApplicationCall.getBearerToken(): String? =
    this.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")

fun ApplicationCall.getCallId(): String {
    return this.request.headers[NAV_CALL_ID_HEADER].toString()
}
