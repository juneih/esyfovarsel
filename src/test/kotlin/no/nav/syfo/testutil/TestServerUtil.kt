package no.nav.syfo.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.features.*
import io.ktor.jackson.*

 fun contentNegotationFeature(): ContentNegotiation.Configuration.() -> Unit {
     return {
         jackson {
             registerKotlinModule()
             registerModule(JavaTimeModule())
             configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
             configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
         }
     }
 }
