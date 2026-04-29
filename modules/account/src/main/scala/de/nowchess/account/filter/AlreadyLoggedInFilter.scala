package de.nowchess.account.filter

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.container.{ContainerRequestContext, ContainerRequestFilter}
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.jwt.JsonWebToken
import scala.compiletime.uninitialized

@Provider
@ApplicationScoped
class AlreadyLoggedInFilter extends ContainerRequestFilter:

  @Inject
  // scalafix:off DisableSyntax.var
  var jwt: JsonWebToken = uninitialized
  // scalafix:on

  override def filter(context: ContainerRequestContext): Unit =
    val path   = context.getUriInfo.getPath
    val method = context.getMethod

    if isProtectedEndpoint(path, method) && isAuthenticated then
      context.abortWith(
        Response
          .status(Response.Status.BAD_REQUEST)
          .entity("""{"error":"Already logged in"}""")
          .build(),
      )

  private def isAuthenticated: Boolean =
    // scalafix:off DisableSyntax.null
    try jwt.getName != null
    catch
      case _ => false
      // scalafix:on DisableSyntax.null

  private def isProtectedEndpoint(path: String, method: String): Boolean =
    (path.contains("/api/account") || path.contains("/account")) &&
      ((path.endsWith("/api/account") && method == "POST") ||
        (path.endsWith("/account") && method == "POST") ||
        (path.contains("/login") && method == "POST"))
