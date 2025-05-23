package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.container

import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.IOException
import java.net.ServerSocket

object PostgresContainer {
  private val log = LoggerFactory.getLogger(this::class.java)
  val instance: PostgreSQLContainer<Nothing>? by lazy { startPostgresqlIfNotRunning() }
  private fun startPostgresqlIfNotRunning(): PostgreSQLContainer<Nothing>? {
    if (isPostgresRunning()) {
      return null
    }

    val logConsumer = Slf4jLogConsumer(log).withPrefix("postgresql")

    return PostgreSQLContainer<Nothing>("postgres:17").apply {
      withEnv("HOSTNAME_EXTERNAL", "localhost")
      withDatabaseName("calculate_release_dates")
      withUsername("calculate_release_dates")
      withPassword("calculate_release_dates")
      setCommand("postgres", "-c", "max_connections=1000")
      setWaitStrategy(Wait.forListeningPort())
      withReuse(false)
      start()
      followOutput(logConsumer)
    }
  }

  private fun isPostgresRunning(): Boolean = try {
    val serverSocket = ServerSocket(5432)
    serverSocket.localPort == 0
  } catch (_: IOException) {
    true
  }
}
