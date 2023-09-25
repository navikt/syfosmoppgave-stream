#  SYFO sm stream oppgave
This project contains creating task to Gsak for the sykmelding2013 message with kafka-streaming

## Technologies used
* Kotlin
* Ktor
* Gradle
* Junit
* Kafka

#### Requirements

* JDK 17

## Getting started
#### Compile and package application

To build locally and run the integration tests you can simply run
``` bash 
./gradlew shadowJar
```
or on windows
`gradlew.bat shadowJar`


### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

```./gradlew wrapper --gradle-version $gradleVersjon```

### Contact

This project is maintained by navikt/teamsykmelding

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/syfosmoppgave-stream/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)