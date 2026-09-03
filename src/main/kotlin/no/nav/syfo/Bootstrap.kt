package no.nav.syfo

import io.prometheus.client.hotspot.DefaultExports
import java.time.Duration
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.model.RegistrerOppgaveKafkaMessage
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmoppgave-stream")

private val jsonMapper: JsonMapper = jacksonMapperBuilder().build()

fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(env, applicationState)
    createAndStartKafkaStream(env, applicationState)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
}

fun createAndStartKafkaStream(env: Environment, applicationState: ApplicationState) {
    val streamBuilder = StreamsBuilder()
    val streamProperties =
        KafkaUtils.getAivenKafkaConfig("syfosmoppgave-stream")
            .toStreamsConfig(env.applicationName, Serdes.ByteArray()::class)
    streamProperties[StreamsConfig.APPLICATION_ID_CONFIG] = env.applicationId
    val journalOpprettetStream =
        streamBuilder.stream(
            env.oppgaveJournalOpprettet,
            Consumed.with(Serdes.String(), Serdes.ByteArray()),
        )
    val produserOppgaveStream =
        streamBuilder.stream(
            env.oppgaveProduserOppgave,
            Consumed.with(Serdes.String(), Serdes.ByteArray()),
        )

    val joinWindow = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofDays(14))

    journalOpprettetStream
        .join(
            produserOppgaveStream,
            { journalOpprettet, produserOppgave ->
                jsonMapper.writeValueAsBytes(
                    RegistrerOppgaveKafkaMessage(
                        produserOppgave = jsonMapper.readValue(produserOppgave),
                        journalOpprettet = jsonMapper.readValue(journalOpprettet),
                    )
                )
            },
            joinWindow,
        )
        .to(env.privatRegistrerOppgave)
        .also { log.info("Sendt event to kafka topic ${env.privatRegistrerOppgave}") }

    val kafkaStream = KafkaStreams(streamBuilder.build(), streamProperties)

    kafkaStream.setUncaughtExceptionHandler { err ->
        log.error("Caught exception in stream, shutting down client: ${err.message}", err)
        StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT
    }

    kafkaStream.setStateListener { newState, oldState ->
        log.info("From state={} to state={}", oldState, newState)
        if (newState == KafkaStreams.State.ERROR || newState == KafkaStreams.State.NOT_RUNNING) {
            log.error("Stream stopped in state {}, marking application as not alive", newState)
            applicationState.ready = false
            applicationState.alive = false
        }
    }

    kafkaStream.start()
}
