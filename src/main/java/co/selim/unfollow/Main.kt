package co.selim.unfollow

import io.vertx.core.Vertx

fun main() {
    Vertx.vertx().deployVerticle(FollowerTrackingVerticle::class.java.name)
}