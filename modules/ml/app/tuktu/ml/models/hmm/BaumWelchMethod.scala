package tuktu.ml.models.hmm

/**
 * Runs the Baum-Welch algorithm for training
 *
 * Implementation from https://github.com/balshor/shimm
 */
class BaumWelchMethod(val samples: Map[Seq[Int], Int]) {

    def apply(initial: HiddenMarkovModel): HiddenMarkovModel = {
        import initial.{ numberOfStates, numberOfObservations }

        val next = new HiddenMarkovModel(numberOfStates, numberOfObservations)
        val aDenominators = new Array[Double](numberOfStates)
        val bDenominators = new Array[Double](numberOfObservations)

        val sampleCount = samples.values.sum

        samples foreach {
            case (observations, count) =>
                val algorithm = new ForwardBackwardAlgorithm(observations)(initial)
                import algorithm.{ alpha, beta, gamma, xi }

                val T = observations.size
                (0 until numberOfStates) foreach { i =>
                    next.Pi(i) += gamma(1, i) * count
                    (1 to T - 1) foreach { t =>
                        (0 until numberOfStates) foreach { j =>
                            next.A(i, j) += xi(t, i, j) * count
                            aDenominators(i) += gamma(t, i) * count
                        }
                    }

                    (1 to T) foreach { t =>
                        (0 until numberOfObservations) foreach { k =>
                            val g = gamma(t, i) * count
                            if (observations(t - 1) == k) {
                                next.B(i, k) += g
                            }
                            bDenominators(k) += g
                        }
                    }
                }
        }

        (0 until numberOfStates) foreach { i =>
            (0 until numberOfStates) foreach { j =>
                next.A(i, j) = if (next.A(i, j) == 0) 0 else next.A(i, j) / aDenominators(i)
            }
            (0 until numberOfObservations) foreach { k =>
                next.B(i, k) = if (next.B(i, k) == 0) 0 else next.B(i, k) / bDenominators(k)
            }
        }

        next.normalize()
        next
    }

}