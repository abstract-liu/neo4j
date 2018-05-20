/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v3_5

import org.opencypher.v9_0.util.{CypherException, InputPosition, SyntaxException}
import org.opencypher.v9_0.ast.semantics.SemanticErrorDef

class SyntaxExceptionCreator(queryText: String, preParserOffset: Option[InputPosition]) extends ((String, InputPosition) => CypherException) {
  override def apply(message: String, position: InputPosition): CypherException = {
    val adjustedPosition = position.withOffset(preParserOffset)
    new SyntaxException(s"$message ($adjustedPosition)", queryText, adjustedPosition)
  }
}

object SyntaxExceptionCreator {
  def throwOnError(mkException: SyntaxExceptionCreator): (Seq[SemanticErrorDef]) => Unit =
    (errors: Seq[SemanticErrorDef]) => errors.foreach(e => throw mkException(e.msg, e.position))
}
