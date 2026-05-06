package de.nowchess.security

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.core.MultivaluedMap
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.ext.DefaultClientHeadersFactoryImpl

import scala.compiletime.uninitialized

@ApplicationScoped
class InternalClientHeadersFactory extends DefaultClientHeadersFactoryImpl {

  @ConfigProperty(name = "nowchess.internal.secret", defaultValue = "")
  // scalafix:off DisableSyntax.var
  var secret: String = uninitialized

  @ConfigProperty(name = "nowchess.internal.auth.enabled", defaultValue = "true")
  var authEnabled: Boolean = uninitialized
  // scalafix:on DisableSyntax.var

  override def update(
      incomingHeaders: MultivaluedMap[String, String],
      clientOutgoingHeaders: MultivaluedMap[String, String],
  ): MultivaluedMap[String, String] = {
    val default = super.update(incomingHeaders, clientOutgoingHeaders)
    default.putSingle("X-Internal-Secret", secret)
    default
  }

}
