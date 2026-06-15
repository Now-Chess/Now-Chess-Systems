FROM apache/spark:3.5.4-scala2.13-java17-ubuntu

USER root

# analytics.jar = fat jar containing app code + PostgreSQL JDBC driver + Scala 3 runtime.
# Spark itself is provided by the base image at /opt/spark — it is NOT included in the jar.
COPY build/libs/analytics.jar /app/analytics.jar

USER spark
