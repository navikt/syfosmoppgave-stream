package no.nav.syfo.application

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.metrics.monitorHttpRequests
import no.nav.syfo.log

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(Netty, env.applicationPort) {
        install(ContentNegotiation) { jackson {} }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                log.error("Caught exception", cause)
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            }
        }

        routing { registerNaisApi(applicationState) }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
