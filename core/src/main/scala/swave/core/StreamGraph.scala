/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package swave.core

import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import scala.concurrent.Future
import swave.core.impl.stages.StageImpl
import swave.core.impl.{RunContext, TypeLogic}
import swave.core.util._

/**
  * A [[StreamGraph]] represents a stream graph in which the ports of all stages
  * have been properly connected and which is therefore ready to be started.
  *
  * If the type parameter `A` is a [[Future]] then `run().result` returns an `A`, otherwise a `Try[A]`.
  */
final class StreamGraph[+A] private[core] (val result: A, stageImpl: StageImpl) {

  /**
    * A [[Stage]] of the graph that can serve as a basis for exploring the graph's stage layout.
    * Often used for rendering via the [[Graph.render]] method.
    */
  def stage: Stage = stageImpl

  /**
    * Turns this [[StreamGraph]] into one with a different result by mapping over the result value.
    *
    * NOTE: The result of this call and the underlying [[StreamGraph]] share the same stages.
    * This means that only one of them can be sealed and/or run (once).
    */
  def mapResult[B](f: A ⇒ B): StreamGraph[B] = new StreamGraph(f(result), stageImpl)

  /**
    * Prepares this [[StreamGraph]] for starting and verifies that the ports of all stages are properly connected.
    *
    * If there is any problem with the stream setup (for example because the graph is not closed or dispatcher
    * assignments are conflicting) a respective [[Exception]] will be thrown.
    *
    * The non-throwing variant of this method is [[StreamGraph.trySeal]]
    */
  def seal()(implicit env: StreamEnv): RunnableStreamGraph[A] = {
    val ctx = RunContext.seal(stageImpl, env)
    new RunnableStreamGraph(result, ctx)
  }

  /**
    * Same as [[StreamGraph.seal]] but never throwing.
    */
  def trySeal()(implicit env: StreamEnv): Try[RunnableStreamGraph[A]] =
    try Success(seal())
    catch { case NonFatal(e) => Failure(e) }

  /**
    * Seals and starts this [[StreamGraph]] and returns the [[StreamRun]] instance for the run.
    * The `result` of the returned [[StreamRun]] has either type `A` if `A` is a [[Future]], or otherwise `Try[A]`.
    *
    * If the stream runs synchronously the call will not return before the stream has finished running completely.
    * In this case the `result` of the returned [[StreamRun]] will be already completed if it's a [[Future]].
    *
    * Otherwise, if the stream runs asynchronously, it will return (more or less) immediately and the stream
    * will run detached from the caller thread.
    *
    * If there are any problems with sealing the stream setup a respective [[Exception]] will be thrown.
    * The non-throwing variant of this method is [[StreamGraph.tryRun]].
    */
  def run()(implicit env: StreamEnv, ev: TypeLogic.ToTryOrFuture[A]): StreamRun[ev.Out] = {
    val ctx = RunContext.seal(stageImpl, env)
    val res =
      try {
        ctx.impl.start()
        ev.success(result)
      } catch {
        case NonFatal(e) ⇒ ev.failure(e)
      }
    new StreamRun(res, ctx)
  }

  /**
    * Same as [[StreamGraph.run]] but never throwing.
    */
  def tryRun()(implicit env: StreamEnv, ev: TypeLogic.ToTryOrFuture[A]): Try[StreamRun[ev.Out]] =
    try Success(run())
    catch { case NonFatal(e) => Failure(e) }
}

/**
  * R [[RunnableStreamGraph]] represents a stream graph that has already been sealed and that is ready to be run.
  *
  * If the type parameter `A` is a [[Future]] then `run().result` returns an `A`, otherwise a `Try[A]`.
  */
final class RunnableStreamGraph[+A] private[core] (val result: A, ctx: RunContext) {

  /**
    * Entry points for exploring the structure of the graph.
    */
  def regions: List[Stage.Region] = ctx.impl.regions

  /**
    * The total number of stages in the underlying stream graph.
    */
  def stagesTotalCount: Int = regions.sumBy(_.stagesTotalCount)

  /**
    * Turns this [[RunnableStreamGraph]] into one with a different result by mapping over the result value.
    *
    * NOTE: The result of this call and the underlying [[RunnableStreamGraph]] share the same stages.
    * This means that only one of them can be run (once).
    */
  def mapResult[B](f: A ⇒ B): RunnableStreamGraph[B] = new RunnableStreamGraph(f(result), ctx)

  /**
    * Starts this [[RunnableStreamGraph]] and returns the [[StreamRun]] instance for the run.
    * The `result` of the returned [[StreamRun]] has either type `A` if `A` is a [[Future]], or otherwise `Try[A]`.
    *
    * If the stream runs synchronously the call will not return before the stream has finished running completely.
    * In this case any returned [[Future]] will be already completed.
    *
    * Otherwise, if the stream runs asynchronously, it will return (more or less) immediately and the stream
    * will run detached from the caller thread.
    */
  def run()(implicit ev: TypeLogic.ToTryOrFuture[A]): StreamRun[ev.Out] = {
    val res =
      try {
        ctx.impl.start()
        ev.success(result)
      } catch {
        case NonFatal(e) ⇒ ev.failure(e)
      }
    new StreamRun(res, ctx)
  }
}

/**
  * A [[StreamRun]] represents a stream graph that has already been started and thus is either running or
  * has already terminated.
  *
  * @tparam A the type of the `result` member
  */
final class StreamRun[+A] private[core] (val result: A, ctx: RunContext) {

  /**
    * Entry points for exploring the stage graph structure.
    */
  def regions: List[Stage.Region] = ctx.impl.regions

  /**
    * The total number of stages in the underlying stream graph.
    */
  def stagesTotalCount: Int = regions.sumBy(_.stagesTotalCount)

  /**
    * The total number of active stages in the underlying stream graph.
    * Note that this method is inherently racy!
    * It is mainly provided for debugging help and not for driving actual business logic.
    */
  def stagesActiveCount: Int = regions.sumBy(_.stagesActiveCount)

  /**
    * Turns this [[StreamRun]] into one with a different result by mapping over the result value.
    */
  def mapResult[B](f: A ⇒ B): StreamRun[B] = new StreamRun(f(result), ctx)

  /**
    * A [[Future]] that is completed when all stages of the graph, in all regions, have terminated.
    * This future can, for example, serve as a trigger for safely shutting down the [[StreamEnv]].
    */
  def termination: Future[Unit] = ctx.impl.termination
}
