package com.zyra.app.logic.ml

import kotlin.math.exp
import kotlin.random.Random

/**
 * A Custom "Extreme" Multi-Layer Perceptron (MLP) Neural Network.
 * Built from scratch to demonstrate offline intelligence without external libraries.
 * Supports:
 * - Feed Forward
 * - Backpropagation (Learning)
 * - Sigmoid Activation
 * - Dynamic Layer Configuration
 */
class NeuralNetwork(
    private val inputSize: Int,
    private val hiddenSize: Int,
    private val outputSize: Int,
    private val learningRate: Double = 0.1
) {

    // Weights and Biases
    private var weightsInputHidden: Array<DoubleArray>
    private var weightsHiddenOutput: Array<DoubleArray>
    private var biasHidden: DoubleArray
    private var biasOutput: DoubleArray

    init {
        // Initialize with random weights (He/Xavier initialization approximation)
        weightsInputHidden = Array(inputSize) { DoubleArray(hiddenSize) { Random.nextDouble(-1.0, 1.0) } }
        weightsHiddenOutput = Array(hiddenSize) { DoubleArray(outputSize) { Random.nextDouble(-1.0, 1.0) } }
        biasHidden = DoubleArray(hiddenSize) { Random.nextDouble(-0.5, 0.5) }
        biasOutput = DoubleArray(outputSize) { Random.nextDouble(-0.5, 0.5) }
    }

    /**
     * Prediction (Forward Pass)
     */
    fun predict(inputs: DoubleArray): DoubleArray {
        // 1. Hidden Layer
        val hiddenInputs = DoubleArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var sum = 0.0
            for (i in 0 until inputSize) {
                sum += inputs[i] * weightsInputHidden[i][j]
            }
            sum += biasHidden[j]
            hiddenInputs[j] = sigmoid(sum)
        }

        // 2. Output Layer
        val outputs = DoubleArray(outputSize)
        for (k in 0 until outputSize) {
            var sum = 0.0
            for (j in 0 until hiddenSize) {
                sum += hiddenInputs[j] * weightsHiddenOutput[j][k]
            }
            sum += biasOutput[k]
            outputs[k] = sigmoid(sum)
        }

        return outputs
    }

    /**
     * Train the network (Backpropagation)
     */
    fun train(inputs: DoubleArray, targets: DoubleArray) {
        // --- Forward Pass (Need intermediate values for backprop) ---
        
        // Hidden Layer
        val hiddenOutputs = DoubleArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var sum = 0.0
            for (i in 0 until inputSize) {
                sum += inputs[i] * weightsInputHidden[i][j]
            }
            sum += biasHidden[j]
            hiddenOutputs[j] = sigmoid(sum)
        }

        // Output Layer
        val finalOutputs = DoubleArray(outputSize)
        for (k in 0 until outputSize) {
            var sum = 0.0
            for (j in 0 until hiddenSize) {
                sum += hiddenOutputs[j] * weightsHiddenOutput[j][k]
            }
            sum += biasOutput[k]
            finalOutputs[k] = sigmoid(sum)
        }

        // --- Backward Pass (Error Calculation & Weight Update) ---

        // 1. Output Errors
        val outputErrors = DoubleArray(outputSize)
        for (k in 0 until outputSize) {
            val error = targets[k] - finalOutputs[k]
            // Derivative of Sigmoid: output * (1 - output)
            outputErrors[k] = error * (finalOutputs[k] * (1.0 - finalOutputs[k]))
        }

        // 2. Hidden Errors
        val hiddenErrors = DoubleArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var error = 0.0
            for (k in 0 until outputSize) {
                error += outputErrors[k] * weightsHiddenOutput[j][k]
            }
            hiddenErrors[j] = error * (hiddenOutputs[j] * (1.0 - hiddenOutputs[j]))
        }

        // 3. Update Weights & Biases (Hidden -> Output)
        for (k in 0 until outputSize) {
            for (j in 0 until hiddenSize) {
                weightsHiddenOutput[j][k] += learningRate * outputErrors[k] * hiddenOutputs[j]
            }
            biasOutput[k] += learningRate * outputErrors[k]
        }

        // 4. Update Weights & Biases (Input -> Hidden)
        for (j in 0 until hiddenSize) {
            for (i in 0 until inputSize) {
                weightsInputHidden[i][j] += learningRate * hiddenErrors[j] * inputs[i]
            }
            biasHidden[j] += learningRate * hiddenErrors[j]
        }
    }

    private fun sigmoid(x: Double): Double {
        return 1.0 / (1.0 + exp(-x))
    }
}
