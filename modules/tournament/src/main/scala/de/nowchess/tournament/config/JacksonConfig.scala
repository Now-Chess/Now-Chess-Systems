package de.nowchess.tournament.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.inject.Singleton

@Singleton
class JacksonConfig extends ObjectMapperCustomizer:
  def customize(objectMapper: ObjectMapper): Unit =
    objectMapper.registerModule(DefaultScalaModule)
