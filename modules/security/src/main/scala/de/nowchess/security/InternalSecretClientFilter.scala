package de.nowchess.security

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.client.{ClientRequestContext, ClientRequestFilter}
import org.eclipse.microprofile.config.inject.ConfigProperty
import scala.compiletime.uninitialized

@ApplicationScoped
class InternalSecretClientFilter extends ClientRequestFilter:

  @ConfigProperty(name = "nowchess.internal.secret", defaultValue = "")
  // scalafix:off DisableSyntax.var
  var secret: String = uninitialized
  // scalafix:on DisableSyntax.var

  override def filter(ctx: ClientRequestContext): Unit =
    ctx.getHeaders.putSingle("X-Internal-Secret", secret)
