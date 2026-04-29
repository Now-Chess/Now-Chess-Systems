package de.nowchess.coordinator.config

import de.nowchess.coordinator.dto.InstanceMetadata
import de.nowchess.coordinator.resource.MetricsDto
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
  targets = Array(
    classOf[InstanceMetadata],
    classOf[MetricsDto],
  ),
)
class NativeReflectionConfig
