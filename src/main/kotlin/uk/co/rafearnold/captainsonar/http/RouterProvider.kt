package uk.co.rafearnold.captainsonar.http

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import javax.inject.Inject
import javax.inject.Provider

class RouterProvider @Inject constructor(
    private val vertx: Vertx
) : Provider<Router> {
    override fun get(): Router = Router.router(vertx)
}
