package de.nowchess.security

import io.grpc.{Metadata, ServerCall, ServerCallHandler, ServerInterceptor, Status}
import io.quarkus.grpc.GlobalInterceptor
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import scala.compiletime.uninitialized

@GlobalInterceptor
@ApplicationScoped
class InternalGrpcAuthInterceptor extends ServerInterceptor:

  private val secretKey = Metadata.Key.of("x-internal-secret", Metadata.ASCII_STRING_MARSHALLER)

  @ConfigProperty(name = "nowchess.internal.secret", defaultValue = "")
  // scalafix:off DisableSyntax.var
  var secret: String = uninitialized

  @ConfigProperty(name = "nowchess.internal.auth.enabled", defaultValue = "true")
  var authEnabled: Boolean = uninitialized
  // scalafix:on DisableSyntax.var

  override def interceptCall[Req, Resp](
      call: ServerCall[Req, Resp],
      headers: Metadata,
      next: ServerCallHandler[Req, Resp],
  ): ServerCall.Listener[Req] =
    val token = Option(headers.get(secretKey)).getOrElse("")
    if authEnabled && token != secret then
      call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid internal secret"), new Metadata())
      new ServerCall.Listener[Req] {}
    else next.startCall(call, headers)
