package de.nowchess.security

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.container.{ContainerRequestContext, ContainerRequestFilter}
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import scala.compiletime.uninitialized

import java.time.{Duration, Instant}
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Provider
@RateLimited
@ApplicationScoped
class RateLimitFilter extends ContainerRequestFilter:

  @ConfigProperty(name = "nowchess.rate-limit.enabled", defaultValue = "true")
  // scalafix:off DisableSyntax.var
  var enabled: Boolean = uninitialized

  @ConfigProperty(name = "nowchess.rate-limit.requests-per-window", defaultValue = "60")
  var requestsPerWindow: Long = uninitialized

  @ConfigProperty(name = "nowchess.rate-limit.window-seconds", defaultValue = "60")
  var windowSeconds: Long = uninitialized

  @ConfigProperty(name = "nowchess.rate-limit.gatling-secret")
  var gatlingSecret: Optional[String] = uninitialized
  // scalafix:on DisableSyntax.var

  private val log      = Logger.getLogger(classOf[RateLimitFilter])
  private val counters = new ConcurrentHashMap[String, RateWindow]()

  override def filter(ctx: ContainerRequestContext): Unit =
    val ip = clientIp(ctx)
    if enabled && !isGatlingRequest(ctx) && isOverLimit(ip) then
      log.warnf("Rate limit exceeded for IP %s on %s %s", ip, ctx.getMethod, ctx.getUriInfo.getPath)
      ctx.abortWith(Response.status(429).build())

  private def isGatlingRequest(ctx: ContainerRequestContext): Boolean =
    gatlingSecret.isPresent &&
      Option(ctx.getHeaderString("X-Gatling-Secret")).contains(gatlingSecret.get())

  private def isOverLimit(ip: String): Boolean =
    val now = Instant.now()
    val window = counters.compute(
      ip,
      (_, current) =>
        // scalafix:off DisableSyntax.null
        if current == null || isExpired(current.start, now) then RateWindow(now, new AtomicLong(0))
        // scalafix:on DisableSyntax.null
        else current,
    )
    window.count.incrementAndGet() > requestsPerWindow

  private def clientIp(ctx: ContainerRequestContext): String =
    Option(ctx.getHeaderString("X-Forwarded-For"))
      .map(_.split(",").head.trim)
      .orElse(Option(ctx.getHeaderString("X-Real-IP")))
      .getOrElse("unknown")

  private def isExpired(start: Instant, now: Instant): Boolean =
    Duration.between(start, now).getSeconds >= windowSeconds

private final case class RateWindow(start: Instant, count: AtomicLong)
