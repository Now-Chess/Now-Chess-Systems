package de.nowchess.coordinator.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClient

@ApplicationScoped
class BeansProducer:

  @Produces
  @ApplicationScoped
  def kubernetesClient: KubernetesClient =
    KubernetesClientBuilder().build()
