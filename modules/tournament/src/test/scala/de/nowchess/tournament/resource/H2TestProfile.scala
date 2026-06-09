package de.nowchess.tournament.resource

import io.quarkus.test.junit.QuarkusTestProfile
import java.util.Map as JMap

class H2TestProfile extends QuarkusTestProfile:

  override def getConfigOverrides(): JMap[String, String] =
    JMap.of(
      "quarkus.datasource.db-kind",
      "h2",
      "quarkus.datasource.jdbc.url",
      "jdbc:h2:mem:nowchess-tournament;DB_CLOSE_DELAY=-1",
      "quarkus.datasource.username",
      "sa",
      "quarkus.datasource.password",
      "",
      "quarkus.hibernate-orm.schema-management.strategy",
      "drop-and-create",
    )
