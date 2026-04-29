package de.nowchess.security

import io.grpc.{CallOptions, Channel, ClientCall, ClientInterceptor, ForwardingClientCall, Metadata, MethodDescriptor}
import io.quarkus.grpc.GlobalInterceptor
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import scala.compiletime.uninitialized

@GlobalInterceptor
@ApplicationScoped
class InternalGrpcSecretClientInterceptor extends ClientInterceptor:

  private val secretKey = Metadata.Key.of("x-internal-secret", Metadata.ASCII_STRING_MARSHALLER)

  @ConfigProperty(name = "nowchess.internal.secret", defaultValue = "")
  // scalafix:off DisableSyntax.var
  var secret: String = uninitialized
  // scalafix:on DisableSyntax.var

  override def interceptCall[Req, Resp](
      method: MethodDescriptor[Req, Resp],
      callOptions: CallOptions,
      next: Channel,
  ): ClientCall[Req, Resp] =
    new ForwardingClientCall.SimpleForwardingClientCall[Req, Resp](next.newCall(method, callOptions)):
      override def start(responseListener: ClientCall.Listener[Resp], headers: Metadata): Unit =
        headers.put(secretKey, secret)
        super.start(responseListener, headers)
