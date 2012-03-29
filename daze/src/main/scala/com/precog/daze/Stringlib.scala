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
package com.precog
package daze

import bytecode.Library
import bytecode.BuiltInFunc1
import bytecode.BuiltInFunc2

import java.lang.String

import yggdrasil._

object Stringlib extends Stringlib

trait Stringlib extends GenOpcode with ImplLibrary {
  val StringNamespace = Vector("std", "string")

  override def _lib1 = super._lib1 ++ Set(length, trim, toUpperCase, toLowerCase, isEmpty, intern)
  override def _lib2 = super._lib2 ++ Set(equalsIgnoreCase, codePointAt, startsWith, lastIndexOf, concat, endsWith, codePointBefore, substring, matches, compareTo, compareToIgnoreCase, equals, indexOf)

  private def isValidInt(num: BigDecimal): Boolean = {
    try { num.toIntExact; true
    } catch {
      case e:java.lang.ArithmeticException => { false }
    }
  }

  object length extends BIF1(StringNamespace, "length") {
    val operandType = Some(SString)
    val operation: PartialFunction[SValue, SValue] = {
      case SString(str) => SDecimal(str.length)
    }
  }
  object trim extends BIF1(StringNamespace, "trim") {
    val operandType = Some(SString)
    val operation: PartialFunction[SValue, SValue] = {
      case SString(str) => SString(str.trim)
    }
  }
  object toUpperCase extends BIF1(StringNamespace, "toUpperCase") {
    val operandType = Some(SString)
    val operation: PartialFunction[SValue, SValue] = {
      case SString(str) => SString(str.toUpperCase)
    }
  }  
  object toLowerCase extends BIF1(StringNamespace, "toLowerCase") {
    val operandType = Some(SString)
    val operation: PartialFunction[SValue, SValue] = {
      case SString(str) => SString(str.toLowerCase)
    }
  }
  object isEmpty extends BIF1(StringNamespace, "isEmpty") {
    val operandType = Some(SString)
    val operation: PartialFunction[SValue, SValue] = {
      case SString(str) => SBoolean(str.isEmpty)
    }
  }
  object intern extends BIF1(StringNamespace, "intern") {
    val operandType = Some(SString)
    val operation: PartialFunction[SValue, SValue] = {
      case SString(str) => SString(str.intern)
    }
  }
  //object hashCode extends BIF1(StringNamespace, "hashCode") {   //mysterious case - java.lang.ClassCastException
  //  val operandType = Some(SString)
  //  val operation: PartialFunction[SValue, SValue] = {
  //    case SString(str) => SDecimal(str.hashCode)
  //  }
  //}
  object equalsIgnoreCase extends BIF2(StringNamespace, "equalsIgnoreCase") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SBoolean(str.equalsIgnoreCase(v2))
    }
  }
  object codePointAt extends BIF2(StringNamespace, "codePointAt") {
    val operandType = (Some(SString), Some(SDecimal))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SDecimal(v2)) if ((v2 >= 0) && (str.length >= v2 + 1) && isValidInt(v2)) => 
        SDecimal(str.codePointAt(v2.toInt))
    }
  }
  object startsWith extends BIF2(StringNamespace, "startsWith") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SBoolean(str.startsWith(v2))
    }
  }
  object lastIndexOf extends BIF2(StringNamespace, "lastIndexOf") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SDecimal(str.lastIndexOf(v2))
    }
  }
  object concat extends BIF2(StringNamespace, "concat") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SString(str.concat(v2))
    }
  }
  object endsWith extends BIF2(StringNamespace, "endsWith") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SBoolean(str.endsWith(v2))
    }
  }
  object codePointBefore extends BIF2(StringNamespace, "codePointBefore") {
    val operandType = (Some(SString), Some(SDecimal))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SDecimal(v2)) if ((v2 > 0) && (str.length >= v2) && isValidInt(v2)) => 
        SDecimal(str.codePointBefore(v2.toInt))
    }
  }
  object substring extends BIF2(StringNamespace, "substring") {
    val operandType = (Some(SString), Some(SDecimal))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SDecimal(v2)) if ((v2 >= 0) && (str.length >= v2 + 1) && isValidInt(v2)) => 
        SString(str.substring(v2.toInt))
    }
  }
  object matches extends BIF2(StringNamespace, "matches") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SBoolean(str.matches(v2))
    }
  }
  object compareTo extends BIF2(StringNamespace, "compareTo") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SDecimal(str.compareTo(v2))
    }
  }
  object compareToIgnoreCase extends BIF2(StringNamespace, "compareToIgnoreCase") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SDecimal(str.compareToIgnoreCase(v2))
    }
  }
  object equals extends BIF2(StringNamespace, "equals") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SBoolean(str.equals(v2))
    }
  }
  object indexOf extends BIF2(StringNamespace, "indexOf") {
    val operandType = (Some(SString), Some(SString))
    val operation: PartialFunction[(SValue, SValue), SValue] = {
      case (SString(str), SString(v2)) => SDecimal(str.indexOf(v2))
    }
  }
}