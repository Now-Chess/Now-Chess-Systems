package de.nowchess.security

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.container.{ContainerRequestContext, ContainerRequestFilter}
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import scala.compiletime.uninitialized

@Provider
@InternalOnly
@ApplicationScoped
class InternalAuthFilter extends ContainerRequestFilter:

  @ConfigProperty(name = "nowchess.internal.secret", defaultValue = "")
  // scalafix:off DisableSyntax.var
  var secret: String = uninitialized

  @ConfigProperty(name = "nowchess.internal.auth.enabled", defaultValue = "true")
  var authEnabled: Boolean = uninitialized
  // scalafix:on DisableSyntax.var

  override def filter(ctx: ContainerRequestContext): Unit =
    if authEnabled then
      val header = Option(ctx.getHeaderString("X-Internal-Secret"))
      if header.isEmpty || header.get != secret then
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build())
