package co.selim.unfollow

import io.vertx.core.logging.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FollowerUpdateLog(logFile: String) {
    private val logPath = Paths.get(logFile)
    private val logger = LoggerFactory.getLogger(FollowerUpdateLog::class.java)

    fun log(followerUpdates: List<String>) {
        val date = today()
        Files.newBufferedWriter(
            logPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        ).use { writer ->
            followerUpdates.forEach { update ->
                writer.appendln("$date - $update")
            }
        }
        logger.info("Logged follower updates")
    }

    private fun today() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
}