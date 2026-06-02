package de.nowchess.account.resource

import de.nowchess.security.RateLimitFilter
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

import java.util.Optional

class RateLimitFilterTest:

  private def newFilter(limit: Long = 2L): RateLimitFilter =
    val f = new RateLimitFilter()
    f.enabled = true
    f.requestsPerWindow = limit
    f.windowSeconds = 60
    f.gatlingSecret = Optional.of("gatling-secret")
    f

  private def ctx(ip: String = "10.0.0.1", gatlingSecret: String = "") =
    val c = mock(classOf[ContainerRequestContext])
    when(c.getHeaderString("X-Forwarded-For")).thenReturn(ip)
    if gatlingSecret.nonEmpty then when(c.getHeaderString("X-Gatling-Secret")).thenReturn(gatlingSecret)
    c

  @Test
  def allowsRequestsUpToLimit(): Unit =
    val filter = newFilter()
    for _ <- 1 to 2 do
      val c = ctx()
      filter.filter(c)
      verify(c, never()).abortWith(any())

  @Test
  def blocks429WhenLimitExceeded(): Unit =
    val filter = newFilter(limit = 2L)
    filter.filter(ctx("1.2.3.4"))
    filter.filter(ctx("1.2.3.4"))
    val c = ctx("1.2.3.4")
    filter.filter(c)
    val captor = ArgumentCaptor.forClass(classOf[Response])
    verify(c).abortWith(captor.capture())
    assertEquals(429, captor.getValue.getStatus)

  @Test
  def gatlingSecretBypasses(): Unit =
    val filter = newFilter(limit = 1L)
    for _ <- 1 to 5 do
      val c = ctx("1.2.3.5", "gatling-secret")
      filter.filter(c)
      verify(c, never()).abortWith(any())

  @Test
  def emptyGatlingSecretDisablesGatlingBypass(): Unit =
    val filter = newFilter(limit = 1L)
    filter.gatlingSecret = Optional.empty()
    filter.filter(ctx("2.2.2.2", "gatling-secret"))
    val c = ctx("2.2.2.2", "gatling-secret")
    filter.filter(c)
    verify(c).abortWith(any())

  @Test
  def doesNothingWhenDisabled(): Unit =
    val filter = newFilter()
    filter.enabled = false
    for _ <- 1 to 5 do
      val c = ctx()
      filter.filter(c)
      verify(c, never()).abortWith(any())

  @Test
  def tracksDifferentIpsSeparately(): Unit =
    val filter = newFilter(limit = 1L)
    for ip <- List("10.1.1.1", "10.1.1.2", "10.1.1.3") do
      val c = ctx(ip)
      filter.filter(c)
      verify(c, never()).abortWith(any())

  @Test
  def usesXForwardedForFirstSegment(): Unit =
    val filter = newFilter(limit = 1L)
    filter.filter(ctx("203.0.113.1, 10.0.0.1"))
    val c = ctx("203.0.113.1, 10.0.0.1")
    filter.filter(c)
    verify(c).abortWith(any())
