/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package swave.core.impl.stages.inout

import swave.core.Stage
import swave.core.impl.stages.InOutStage
import swave.core.impl.{Inport, Outport}
import swave.core.macros._

// format: OFF
@StageImplementation
private[core] final class DropStage(count: Long) extends InOutStage {

  requireArg(count > 0, "`count` must be > 0")

  def kind = Stage.Kind.InOut.Drop(count)

  connectInOutAndSealWith { (in, out) ⇒
    region.impl.registerForXStart(this)
    running(in, out)
  }

  def running(in: Inport, out: Outport) = {

    def awaitingXStart() = state(
      xStart = () => {
        in.request(count)
        dropping(count)
      })

    /**
      * Waiting for elements from upstream to drop.
      *
      * @param remaining number of elems still to drop, >0
      */
    def dropping(remaining: Long): State = state(
      request = requestF(in),
      cancel = stopCancelF(in),
      onNext = (_, _) ⇒ if (remaining > 1) dropping(remaining - 1) else draining(),
      onComplete = stopCompleteF(out),
      onError = stopErrorF(out))

    /**
      * Simply forwarding elements from upstream to downstream.
      */
    def draining() = state(
      intercept = false,

      request = requestF(in),
      cancel = stopCancelF(in),
      onNext = onNextF(out),
      onComplete = stopCompleteF(out),
      onError = stopErrorF(out))

    awaitingXStart()
  }
}
