package sttp.tapir.server.netty.sync

import ox.*
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyConfig, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.duration.FiniteDuration
import sttp.capabilities.WebSockets

class NettySyncTestServerInterpreter(eventLoopGroup: NioEventLoopGroup)
    extends TestServerInterpreter[Identity, OxStreams with WebSockets, NettySyncServerOptions, IdRoute] {
  override def route(es: List[ServerEndpoint[OxStreams with WebSockets, Identity]], interceptors: Interceptors): IdRoute = {
    val serverOptions: NettySyncServerOptions = interceptors(NettySyncServerOptions.customiseInterceptors).options
    supervised { // not a correct way, but this method is only used in a few tests which don't test anything related to scopes
      NettySyncServerInterpreter(serverOptions).toRoute(es, inScopeRunner())
    }
  }

  def route(es: List[ServerEndpoint[OxStreams with WebSockets, Identity]], interceptors: Interceptors)(using Ox): IdRoute = {
    val serverOptions: NettySyncServerOptions = interceptors(NettySyncServerOptions.customiseInterceptors).options
    supervised { // not a correct way, but this method is only used in a few tests which don't test anything related to scopes
      NettySyncServerInterpreter(serverOptions).toRoute(es, inScopeRunner())
    }
  }

  override def server(
      routes: NonEmptyList[IdRoute],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, Port] = {
    val config =
      NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettySyncServerOptions.default
    val bind = IO.blocking(NettySyncServer(options, customizedConfig).start(Route.combine(routes.toList)))

    Resource.make(bind)(server => IO.blocking(server.stop())).map(_.port)
  }

  def scopedServerWithRoutesStop(
      routes: NonEmptyList[IdRoute],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(using Ox): NettySyncServerBinding =
    val config =
      NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettySyncServerOptions.default
    useInScope(NettySyncServer(options, customizedConfig).start(Route.combine(routes.toList)))(_.stop())

  def scopedServerWithInterceptorsStop(
      endpoint: ServerEndpoint[OxStreams with WebSockets, Identity],
      interceptors: Interceptors = identity,
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(using Ox): NettySyncServerBinding =
    val config =
      NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = interceptors(NettySyncServerOptions.customiseInterceptors).options
    val route = NettySyncServerInterpreter(options).toRoute(List(endpoint), inScopeRunner())
    useInScope(NettySyncServer(customizedConfig).addRoute(route).start())(_.stop())

  def scopedServerWithStop(
      endpoints: NonEmptyList[ServerEndpoint[OxStreams with WebSockets, Identity]],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(using Ox): NettySyncServerBinding =
    val config =
      NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettySyncServerOptions.default
    useInScope(NettySyncServer(options, customizedConfig).addEndpoints(endpoints.toList).start())(_.stop())
}
