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
package com.precog.ragnarok

import java.io.File

import scala.annotation.tailrec

import scalaz._
import scalaz.syntax.applicative._
import scalaz.syntax.validation._


case class RunConfig(
    format: RunConfig.OutputFormat = RunConfig.OutputFormat.Legible,
    select: Option[(List[String], PerfTest) => Boolean] = None,
    runs: Int = 60,
    outliers: Double = 0.05,
    dryRuns: Int = 10,
    optimize: Boolean = true,
    baseline: Option[File] = None) {
  def tails: Int = (runs * (outliers / 2)).toInt
}


object RunConfig {

  sealed trait OutputFormat
  object OutputFormat {
    case object Json extends OutputFormat
    case object Legible extends OutputFormat
  }


  implicit object semigroup extends Semigroup[RunConfig] {
    def append(a: RunConfig, b: => RunConfig) = b
  }


  val NonNegativeInt = """([0-9]+)""".r
  val PositiveInt = """([1-9][0-9]*)""".r

  object OutlierPercentage {
    def unapply(str: String): Option[Double] = try {
      val p = str.toDouble
      if (p >= 0.0 && p < 0.5) Some(p) else None
    } catch {
      case _: NumberFormatException =>
        None
    }
  }


  @tailrec
  def fromCommandLine(args: List[String], config: ValidationNEL[String, RunConfig] = RunConfig().successNel): ValidationNEL[String, RunConfig] = args match {
    case Nil =>
      config

    case "--baseline" :: file :: args =>
      val f = new File(file)
      if (f.isFile && f.canRead) {
        fromCommandLine(args, config map (_. copy(baseline = Some(f))))
      } else {
        fromCommandLine(args, config *> "The baseline file must be regular and readable.".failNel)
      }

    case "--json" :: args =>
      fromCommandLine(args, config map (_.copy(format = OutputFormat.Json)))

    case "--no-optimize" :: args =>
      fromCommandLine(args, config map (_.copy(optimize = true)))

    case "--dry-runs" :: NonNegativeInt(runs) :: args =>
      fromCommandLine(args, config map (_.copy(dryRuns = runs.toInt)))

    case "--dry-runs" :: _ :: args =>
      fromCommandLine(args, config *> "The argument to --runs must be a positive integer".failNel)

    case "--runs" :: PositiveInt(runs) :: args =>
      fromCommandLine(args, config map (_.copy(runs = runs.toInt)))

    case "--runs" :: _ :: args =>
      fromCommandLine(args, config *> "The argument to --runs must be a positive integer".failNel)

    case "--outliers" :: OutlierPercentage(outliers) :: args =>
      fromCommandLine(args, config map (_.copy(outliers = outliers)))

    case "--outliers" :: _ :: args =>
      fromCommandLine(args, config *> "The argument to --outliers must be a real number in [0, 0.5)".failNel)

    case test :: args =>
      fromCommandLine(args, config map { config =>
        val g = { (path: List[String], _: Any) => path contains test }
        val select = config.select map { f =>
          (path: List[String], test: PerfTest) => (f(path, test) || g(path, test))
        } orElse Some(g)

        config.copy(select = select)
      })
  }
}
