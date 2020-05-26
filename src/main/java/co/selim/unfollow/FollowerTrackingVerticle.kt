package co.selim.unfollow

import io.vertx.config.ConfigRetriever
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.kotlin.config.getConfigAwait
import io.vertx.kotlin.core.file.existsAwait
import io.vertx.kotlin.core.http.endAwait
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.core.http.sendFileAwait
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.awaitBlocking
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch
import org.brunocvcunha.instagram4j.Instagram4j
import org.brunocvcunha.instagram4j.requests.InstagramGetUserFollowersRequest
import org.brunocvcunha.instagram4j.requests.InstagramSearchUsernameRequest
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import java.util.concurrent.TimeUnit

class FollowerTrackingVerticle : CoroutineVerticle() {
    private val logger = LoggerFactory.getLogger(FollowerTrackingVerticle::class.java)

    override suspend fun start() {
        val config = ConfigRetriever
            .create(vertx)
            .getConfigAwait()

        logger.info("Logging in...")
        val instagramClient = login(config["username"], config["password"])
        val userId = instagramClient.getUserId(config["tracked-user"])
        val logFile: String = config["log-file"]
        val followerLog = FollowerUpdateLog(logFile)
        val followerStore = FileBackedFollowerStore(config["follower-file"])

        followerStore.followers = instagramClient.checkForUpdates(userId, followerLog, followerStore.followers)

        vertx.setPeriodic(TimeUnit.HOURS.toMillis(8)) {
            launch(vertx.dispatcher()) {
                followerStore.followers = instagramClient.checkForUpdates(userId, followerLog, followerStore.followers)
            }
        }

        startHttpServer(config["port"], logFile)
    }

    suspend fun startHttpServer(port: Int, logFile: String) {
        val router = Router.router(vertx)
        router.get("/unfollowers")
            .handler(LoggerHandler.create())
            .handler { routingContext ->
                launch(vertx.dispatcher()) {
                    sendFollowerUpdates(logFile, routingContext)
                }
            }

        vertx.createHttpServer()
            .requestHandler(router)
            .listenAwait(port)
    }

    private suspend fun sendFollowerUpdates(logFile: String, routingContext: RoutingContext) {
        val logFileExists = vertx.fileSystem().existsAwait(logFile)
        if (logFileExists) {
            routingContext.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "text/plain")
                .sendFileAwait(logFile)
        } else {
            routingContext.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "text/plain")
                .endAwait("No updates\n")
        }
    }

    private suspend fun Instagram4j.checkForUpdates(
        userId: Long,
        followerUpdateLog: FollowerUpdateLog,
        previousFollowers: List<String>
    ): List<String> {
        logger.info("Fetching followers...")
        val followers = getFollowers(userId)
        val unfollowers = previousFollowers - followers
        val newFollowers = followers - previousFollowers
        if (unfollowers.isNotEmpty()) {
            logger.info("New unfollowers: $unfollowers")
            followerUpdateLog.log(unfollowers.map { "- $it" })
        }
        if (newFollowers.isNotEmpty()) {
            logger.info("New followers: $newFollowers")
            followerUpdateLog.log(newFollowers.map { "+ $it" })
        }
        return followers
    }

    private suspend fun login(username: String, password: String) = awaitBlocking {
        val insta4j = Instagram4j.builder()
            .username(username)
            .password(password)
            .build()
        insta4j.apply {
            setup()
            login()
        }
    }

    private suspend fun Instagram4j.getUserId(username: String): Long = awaitBlocking {
        sendRequest(InstagramSearchUsernameRequest(username))
            .user
            .pk
    }

    private suspend fun Instagram4j.getFollowers(userId: Long) = awaitBlocking {
        val followers = mutableListOf<InstagramUserSummary>()
        val response = sendRequest(InstagramGetUserFollowersRequest(userId))
        followers.addAll(response.users)
        var nextMaxId = response.next_max_id
        while (nextMaxId != null) {
            val nextResponse = sendRequest(InstagramGetUserFollowersRequest(userId, nextMaxId))
            followers.addAll(nextResponse.users)
            nextMaxId = nextResponse.next_max_id
        }

        followers
            .map(InstagramUserSummary::username)
    }
}
