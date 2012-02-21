/*
 *  ____    ____    _____    ____    ___     ____ 
 * |  _ \  |  _ \  | ____|  / ___|  / _/    / ___|        Precog (R)
 * | |_) | | |_) | |  _|   | |     | |  /| | |  _         Advanced Analytics Engine for NoSQL Data
 * |  __/  |  _ <  | |___  | |___  |/ _| | | |_| |        Copyright (C) 2010 - 2013 SlamData, Inc.
 * |_|     |_| \_\ |_____|  \____|   /__/   \____|        All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the 
 * GNU Affero General Public License as published by the Free Software Foundation, either version 
 * 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See 
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this 
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.precog.shard

import service._

import blueeyes._
import blueeyes.bkka._
import blueeyes.core.data.{BijectionsChunkJson, BijectionsChunkFutureJson, BijectionsChunkString, ByteChunk}
import blueeyes.core.http._
import blueeyes.core.http.MimeTypes.{application, json}
import blueeyes.core.service._
import blueeyes.core.service.RestPathPattern._
import blueeyes.health.metrics.{eternity}
import blueeyes.json.JsonAST._
import blueeyes.json.JsonDSL._
import blueeyes.json.{JPath, JsonParser, JPathField}
import blueeyes.json.xschema._
import blueeyes.json.xschema.DefaultOrderings._
import blueeyes.json.xschema.DefaultSerialization._
import blueeyes.json.xschema.JodaSerializationImplicits._
import blueeyes.persistence.mongo._
import blueeyes.persistence.cache.{Stage, ExpirationPolicy, CacheSettings}
import blueeyes.util.{Clock, ClockSystem, PartialFunctionCombinators, InstantOrdering}
import scala.math.Ordered._
import HttpStatusCodes.{BadRequest, Unauthorized, Forbidden}

import akka.dispatch.Future

import net.lag.configgy.{Configgy, ConfigMap}

import org.joda.time.base.AbstractInstant
import org.joda.time.Instant
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

import java.net.URL
import java.util.concurrent.TimeUnit

import scala.collection.immutable.SortedMap
import scala.collection.immutable.IndexedSeq

import scalaz.Monoid
import scalaz.Validation
import scalaz.ValidationNEL
import scalaz.Success
import scalaz.Failure
import scalaz.NonEmptyList
import scalaz.Scalaz._

import com.precog.analytics._
import com.precog.ct._
import com.precog.ct.Mult._
import com.precog.ct.Mult.MDouble._

import com.precog.ingest._
import com.precog.ingest.service._

case class ShardState(queryExecutor: QueryExecutor, indexMongo: Mongo, tokenManager: TokenManager, usageLogging: UsageLogging)

trait ShardService extends BlueEyesServiceBuilder with IngestServiceCombinators {
  import IngestService._
  import BijectionsChunkJson._
  import BijectionsChunkString._
  import BijectionsChunkFutureJson._

  implicit val timeout = akka.util.Timeout(120000) //for now

  def queryExecutorFactory(configMap: ConfigMap): QueryExecutor

  def mongoFactory(configMap: ConfigMap): Mongo

  def usageLogging(configMap: ConfigMap): UsageLogging 

  def tokenManager(database: Database, tokensCollection: MongoCollection, deletedTokensCollection: MongoCollection): TokenManager

  val analyticsService = this.service("ingest", "1.0") {
    requestLogging(timeout) {
      healthMonitor(timeout, List(eternity)) { monitor => context =>
        startup {
          import context._

          val queryExecutor = queryExecutorFactory(config.configMap("query_executor"))

          val indexdbConfig = config.configMap("indexdb")
          val indexMongo = mongoFactory(indexdbConfig)
          val indexdb  = indexMongo.database(indexdbConfig.getString("database", "analytics-v" + serviceVersion))

          val tokensCollection = config.getString("tokens.collection", "tokens")
          val deletedTokensCollection = config.getString("tokens.deleted", "deleted_tokens")
          val tokenMgr = tokenManager(indexdb, tokensCollection, deletedTokensCollection)

          queryExecutor.startup.map { _ =>
            ShardState(
              queryExecutor,
              indexMongo,
              tokenMgr,
              usageLogging(config.configMap("usageLogging"))
            )
          }
        } ->
        request { (state: ShardState) =>
          jsonp[ByteChunk] {
            token(state.tokenManager) {
              path("/query") {
                post(new QueryServiceHandler(state.queryExecutor))
              }
            }
          }
        } ->
        shutdown { state => 
          Future( 
            Option(
              Stoppable(state.tokenManager.database, Stoppable(state.indexMongo) :: Nil)
            )
          )
        }
      }
    }
  }
}

object ShardService extends HttpRequestHandlerCombinators with PartialFunctionCombinators {
  def parsePathInt(name: String) = 
    ((err: NumberFormatException) => DispatchError(BadRequest, "Illegal value for path parameter " + name + ": " + err.getMessage)) <-: (_: String).parseInt

  def validated[A](v: Option[ValidationNEL[String, A]]): Option[A] = v map {
    case Success(a) => a
    case Failure(t) => throw new HttpException(BadRequest, t.list.mkString("; "))
  }

  def vtry[A](value: => A): Validation[Throwable, A] = try { value.success } catch { case ex => ex.fail[A] }
}
