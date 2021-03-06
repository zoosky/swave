/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package swave.docs

import org.scalatest.{FreeSpec, Matchers}

class SpoutSpec extends FreeSpec with Matchers {

  "the examples in the `spouts` chapter should work as expected" - {

    "option-flatmap" in {
      //#option-flatmap
      import scala.concurrent.Future
      import swave.core._

      implicit val env = StreamEnv()

      def evenIntsViaFlatmap =
        Spout.ints(from = 0)
          .flatMap(i => if (i % 2 == 0) Some(i) else None)

      def evenIntsViaFilter =
        Spout.ints(from = 0)
          .filter(_ % 2 == 0)

      val a: Future[List[Int]] = evenIntsViaFlatmap.take(5).drainToList(limit = 5)
      val b: Future[List[Int]] = evenIntsViaFilter.take(5).drainToList(limit = 5)

      // since both streams in this example run synchronously
      // both futures are already fulfilled and we can access
      // their `value` members to compare results
      a.value.get shouldEqual b.value.get
      //#option-flatmap
    }

    "streamable-either" in {
      //#streamable-either
      import scala.util.Success
      import shapeless.Lub
      import swave.core._

      implicit val env = StreamEnv()

      implicit def forEither[A, B, C](implicit ev: Lub[A, B, C]) =
        new Streamable[Either[A, B]] {
          type Out = C
          def apply(value: Either[A, B]): Spout[C] =
            value.fold(a => Spout.one(ev left a), b => Spout.one(ev right b))
        }

      def eitherStream: Spout[Either[(Char, Int), String]] =
        Spout(Left('0' -> 0), Right("1"), Right("2"))

      def anyRefStream: Spout[AnyRef] =
         eitherStream.flattenConcat()

      anyRefStream
        .drainToList(5)
        .value.get shouldEqual Success(List('0' -> 0, "1", "2"))
      //#streamable-either
    }
  }
}
